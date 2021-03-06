package com.puzzletimer.state;

import java.util.ArrayList;
import java.util.Date;
import java.util.TimerTask;

import com.puzzletimer.models.Timing;
import com.puzzletimer.timer.Timer;

public class TimerManager {
    public static class Listener {
        // timer
        public void timerChanged(Timer timer) { }
        public void timerReset() { }

        // hands
        public void leftHandPressed() { }
        public void leftHandReleased() { }
        public void rightHandPressed() { }
        public void rightHandReleased() { }

        // inspection
        public void inspectionEnabledSet(boolean inspectionEnabled) { }
        public void inspectionStarted() { }
        public void inspectionRunning(long remainingTime) { }
        public void inspectionFinished() { }

        // solution
        public void solutionStarted() { }
        public void solutionRunning(Timing timing) { }
        public void solutionFinished(Timing timing, String penalty) { }
    }

    private ArrayList<Listener> listeners;
    private Timer currentTimer;
    private boolean inspectionEnabled;
    private java.util.Timer repeater;
    private Date inspectionStart;
    private String penalty;

    public TimerManager() {
        this.listeners = new ArrayList<Listener>();
        this.currentTimer = null;
        this.inspectionEnabled = false;
        this.repeater = null;
        this.inspectionStart = null;
        this.penalty = "";
    }


    // timer

    public void setTimer(Timer timer) {
        // suspend running inspection
        if (this.inspectionStart != null) {
            this.repeater.cancel();
            this.inspectionStart = null;
            this.penalty = "";
        }

        if (this.currentTimer != null) {
            this.currentTimer.stop();
        }

        this.currentTimer = timer;
        this.currentTimer.setInspectionEnabled(this.inspectionEnabled);

        for (Listener listener : this.listeners) {
            listener.timerChanged(timer);
        }

        this.currentTimer.start();
    }

    public void resetTimer() {
        for (Listener listener : this.listeners) {
            listener.timerReset();
        }
    }

    // hands

    public void pressLeftHand() {
        for (Listener listener : this.listeners) {
            listener.leftHandPressed();
        }
    }

    public void releaseLeftHand() {
        for (Listener listener : this.listeners) {
            listener.leftHandReleased();
        }
    }

    public void pressRightHand() {
        for (Listener listener : this.listeners) {
            listener.rightHandPressed();
        }
    }

    public void releaseRightHand() {
        for (Listener listener : this.listeners) {
            listener.rightHandReleased();
        }
    }


    // inspection

    public boolean isInspectionEnabled() {
        return this.inspectionEnabled;
    }

    public void setInspectionEnabled(boolean inspectionEnabled) {
        this.inspectionEnabled = inspectionEnabled;

        if (this.currentTimer != null) {
            this.currentTimer.setInspectionEnabled(inspectionEnabled);
        }

        for (Listener listener : this.listeners) {
            listener.inspectionEnabledSet(inspectionEnabled);
        }
    }

    public void startInspection() {
        for (Listener listener : TimerManager.this.listeners) {
            listener.inspectionStarted();
        }

        this.inspectionStart = new Date();
        this.penalty = "";

        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                long start = TimerManager.this.inspectionStart.getTime();
                long now = new Date().getTime();

                for (Listener listener : TimerManager.this.listeners) {
                    listener.inspectionRunning(15000 - (now - start));
                }

                if (now - start > 17000) {
                    TimerManager.this.repeater.cancel();

                    for (Listener listener : TimerManager.this.listeners) {
                        listener.inspectionFinished();
                    }

                    TimerManager.this.inspectionStart = null;
                    TimerManager.this.penalty = "DNF";

                    finishSolution(new Timing(new Date(now), new Date(now)));
                } else if (now - start > 15000) {
                    TimerManager.this.penalty = "+2";
                }
            }
        };

        this.repeater = new java.util.Timer();
        this.repeater.schedule(timerTask, 0, 10);
    }


    // solution

    public void startSolution() {
        if (this.inspectionStart != null) {
            this.repeater.cancel();
            this.inspectionStart = null;

            for (Listener listener : TimerManager.this.listeners) {
                listener.inspectionFinished();
            }
        }

        for (Listener listener : this.listeners) {
            listener.solutionStarted();
        }
    }

    public void updateSolutionTiming(Timing timing) {
        for (Listener listener : this.listeners) {
            listener.solutionRunning(timing);
        }
    }

    public void finishSolution(Timing timing) {
        for (Listener listener : this.listeners) {
            listener.solutionFinished(timing, this.penalty);
        }

        this.penalty = "";
    }


    // listeners

    public void addListener(Listener listener) {
        this.listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        this.listeners.remove(listener);
    }
}
