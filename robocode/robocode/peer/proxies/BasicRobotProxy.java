/*******************************************************************************
 * Copyright (c) 2001, 2008 Mathew A. Nelson and Robocode contributors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://robocode.sourceforge.net/license/cpl-v10.html
 *
 * Contributors:
 *     Pavel Savara
 *     - Initial implementation
 *******************************************************************************/
package robocode.peer.proxies;


import robocode.Bullet;
import robocode.RobotStatus;
import robocode.Condition;
import robocode.peer.RobotPeer;
import robocode.peer.RobotCommands;
import robocode.peer.ExecResult;
import robocode.exception.*;
import robocode.robotinterfaces.peer.IBasicRobotPeer;

import java.awt.*;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * @author Pavel Savara (original)
 */
public class BasicRobotProxy implements IBasicRobotPeer {
    private static final long
            MAX_SET_CALL_COUNT = 10000,
            MAX_GET_CALL_COUNT = 10000;

    protected RobotPeer peer;
    protected RobotStatus status;
    protected RobotCommands commands;

    private AtomicInteger setCallCount = new AtomicInteger(0);
    private AtomicInteger getCallCount = new AtomicInteger(0);

    protected Condition waitCondition;

    public BasicRobotProxy(RobotPeer peer) {
        this.peer = peer;
    }

    public void initialize() {
    }


    public void cleanup() {
        // Cleanup and remove current wait condition
        if (waitCondition != null) {
            waitCondition.cleanup();
            waitCondition = null;
        }
    }

    // asynchronous actions
    public Bullet setFire(double power) {
        return setFireImpl(power);
    }

    // blocking actions
    public void execute() {
        executeImpl();
    }

    public void move(double distance) {
        setMoveImpl(distance);
        do {
            execute(); // Always tick at least once
        } while (getDistanceRemaining() != 0);
    }

    public void turnBody(double radians) {
        setTurnBodyImpl(radians);
        do {
            execute(); // Always tick at least once
        } while (getBodyTurnRemaining() != 0);
    }

    public void turnGun(double radians) {
        setTurnGunImpl(radians);
        do {
            execute(); // Always tick at least once
        } while (getGunTurnRemaining() != 0);
    }

    public Bullet fire(double power) {
        Bullet bullet = setFire(power);

        execute();
        return bullet;
    }

    // fast setters
    public void setBodyColor(Color color) {
        setCall();
        commands.setBodyColor(color);
    }

    public void setGunColor(Color color) {
        setCall();
        commands.setGunColor(color);
    }

    public void setRadarColor(Color color) {
        setCall();
        commands.setRadarColor(color);
    }

    public void setBulletColor(Color color) {
        setCall();
        commands.setBulletColor(color);
    }

    public void setScanColor(Color color) {
        setCall();
        commands.setScanColor(color);
    }

    // counters
    public void setCall() {
        final int res = setCallCount.incrementAndGet();

        if (res >= MAX_SET_CALL_COUNT) {
            peer.getOut().println("SYSTEM: You have made " + res + " calls to setXX methods without calling execute()");
            throw new DisabledException("Too many calls to setXX methods");
        }
    }

    public void getCall() {
        final int res = getCallCount.incrementAndGet();

        if (res >= MAX_GET_CALL_COUNT) {
            peer.getOut().println("SYSTEM: You have made " + res + " calls to getXX methods without calling execute()");
            throw new DisabledException("Too many calls to getXX methods");
        }
    }

    public double getDistanceRemaining() {
        getCall();
        return commands.getDistanceRemaining();
    }

    public double getRadarTurnRemaining() {
        getCall();
        return commands.getRadarTurnRemaining();
    }

    public double getBodyTurnRemaining() {
        getCall();
        return commands.getBodyTurnRemaining();
    }

    public double getGunTurnRemaining() {
        getCall();
        return commands.getGunTurnRemaining();
    }

    public double getVelocity() {
        getCall();
        return status.getVelocity();
    }

    public double getGunCoolingRate() {
        getCall();
        return status.getBattleRules().getGunCoolingRate();
    }

    public String getName() {
        getCall();
        return peer.getName();
    }

    public long getTime() {
        getCall();
        return status.getTime();
    }

    public double getBodyHeading() {
        getCall();
        return status.getBodyHeadingRadians();
    }

    public double getGunHeading() {
        getCall();
        return status.getGunHeadingRadians();
    }

    public double getRadarHeading() {
        getCall();
        return status.getRadarHeadingRadians();
    }

    public double getEnergy() {
        getCall();
        return status.getEnergy();
    }

    public double getGunHeat() {
        getCall();
        return status.getGunHeat();
    }

    public double getX() {
        getCall();
        return status.getX();
    }

    public double getY() {
        getCall();
        return status.getY();
    }

    public int getOthers() {
        getCall();
        return status.getOthers();
    }

    public double getBattleFieldHeight() {
        getCall();
        return status.getBattleRules().getBattlefieldHeight();
    }

    public double getBattleFieldWidth() {
        getCall();
        return status.getBattleRules().getBattlefieldWidth();
    }

    public int getNumRounds() {
        getCall();
        return status.getBattleRules().getNumRounds();
    }

    public int getRoundNum() {
        getCall();
        return status.getRoundNum();
    }

    public Graphics2D getGraphics() {
        getCall();
        return peer.getGraphics();
    }

    // -----------
    // implementations
    // -----------

    protected final void executeImpl() {
        // Entering tick
        if (Thread.currentThread() != peer.getRobotThreadManager().getRunThread()) {
            throw new RobotException("You cannot take action in this thread!");
        }
        if (peer.getTestingCondition()) {
            throw new RobotException(
                    "You cannot take action inside Condition.test().  You should handle onCustomEvent instead.");
        }

        setSetCallCount(0);
        setGetCallCount(0);

        // This stops autoscan from scanning...
        if (waitCondition != null && waitCondition.test()) {
            waitCondition = null;
            commands.setScan(true);
        }

        ExecResult result = peer.executeImpl(commands);
        updateStatus(result.commands, result.status);

        // Out's counter must be reset before processing event.
        // Otherwise, it will not be reset when printing in the onScannedEvent()
        // before a scan() call, which will potentially cause a new onScannedEvent()
        // and therefore not be able to reset the counter.
        peer.getOut().resetCounter();

        peer.getEventManager().processEvents();
    }

    protected final void setMoveImpl(double distance) {
        if (Double.isNaN(distance)) {
            peer.getOut().println("SYSTEM: You cannot call move(NaN)");
            return;
        }
        if (getEnergy() == 0) {
            return;
        }
        commands.setDistanceRemaining(distance);
        commands.setMoved(true);
    }

    protected final Bullet setFireImpl(double power) {
        if (Double.isNaN(power)) {
            peer.getOut().println("SYSTEM: You cannot call fire(NaN)");
            return null;
        }
        if (getGunHeat() > 0 || getEnergy() == 0) {
            return null;
        }

        final Bullet bullet = new Bullet(getGunHeading(), getX(), getY(), power, getName());
        commands.getBullets().add(bullet);

        return bullet;
    }

    protected final void setTurnGunImpl(double radians) {
        commands.setGunTurnRemaining(radians);
    }

    protected final void setTurnBodyImpl(double radians) {
        if (getEnergy() > 0) {
            commands.setBodyTurnRemaining(radians);
        }
    }

    protected final void setTurnRadarImpl(double radians) {
        commands.setRadarTurnRemaining(radians);
    }

    // -----------
    // battle driven methods
    // -----------

    public void updateStatus(RobotCommands commands, RobotStatus status) {
        this.status = status;
        this.commands = commands;
    }

    public void setSetCallCount(int setCallCount) {
        this.setCallCount.set(setCallCount);
    }

    public void setGetCallCount(int getCallCount) {
        this.getCallCount.set(getCallCount);
    }
}
