package com.example.keith.heartbeat_thread;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {
    private static final String TAG1 = "Main";
    private static final String TAG2 = "HeartBeat";
    private static final String TAG3 = "Worker";
    private static final int HB_THREAD_SLEEP = 1000;
    private static final int WORKER_THREAD_SLEEP = 200;
    private static final int NUMB_CYCLES = 10;

    private AtomicInteger iNumbWorkers;

    private HB_Thread hbThread;
    private WorkerThread wThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        iNumbWorkers = new AtomicInteger(0);
        hbThread = null;
        wThread = null;
    }



    //used by heartbeat to restart dead worker
    private interface HBCallBack {
        void startWorker();
    }
    HBCallBack cb = new HBCallBack() {
        @Override
        public void startWorker() {
            //it does not matter if this is called
            //over and over in HB_thread since
            //calls to this callback is serialized
            if (wThread == null || !wThread.isAlive()) {
                stopWorker();
                wThread = new WorkerThread(NUMB_CYCLES);
                Log.d(TAG1, "exiting start worker");
            }
            else{
                Log.d(TAG1, "Mybad, worker already running");
            }
        }
    };

    //makes sure worker is running, if not it restarts it
    private class HB_Thread extends Thread {
        protected  final AtomicBoolean doWork;
        protected final AppCompatActivity act;      //for callbacks
        protected final HBCallBack cb;                //for callbacks

        HB_Thread(AppCompatActivity act, HBCallBack cb) {
            super();
            this.act = act;
            this.cb = cb;
            Log.d(TAG2, "HB_Thread constructor");

            doWork = new AtomicBoolean(true);
            start();
        }

        public void run() {
            Log.d(TAG2,"started!");

            while (doWork.get()){
                //snooze a bit
                try {
                    Thread.sleep(HB_THREAD_SLEEP);
                } catch (InterruptedException e) {
                    Log.e(TAG2,e.toString());
                }

                //if not alive or null then restart
                if (wThread == null || !wThread.isAlive()){
                    Log.e(TAG2, "Worker is dead, restarting");
                    startWorkerThread();
                }
                else
                    Log.d(TAG2, "Worker is dandy!");
              }
            Log.d(TAG2, "Heartbeat exiting");
        }

        //tell the main activity to restart
        //let it worry about duplicate request
        private void startWorkerThread() {
            Log.d(TAG2, "Telling activity to start worker!");
            act.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    cb.startWorker();
                }
            });
         }

        public void cancel(){
            Log.d(TAG2,"Canceling HB_thread!");
            doWork.set(false);
         }
    }

    private class WorkerThread extends Thread {
        protected  final AtomicBoolean doWork;
        protected  int numb_cycles;

        WorkerThread(int numb_cycles) {
            super();
            this.numb_cycles = numb_cycles;

            Log.d(TAG3, "worker constructor");
            doWork = new AtomicBoolean(true);
            start();
        }
        public void run() {
            Log.d(TAG3, "Started worker # " + Integer.toString(MainActivity.this.iNumbWorkers.incrementAndGet()));

            while (doWork.get()){
                Log.d(TAG3, "working ....");

                // pause for a bit then exit
                // see if atomic boolean kicks this thread off again
                try {
                    Thread.sleep(WORKER_THREAD_SLEEP);
                } catch (InterruptedException e) {
                    Log.e(TAG3,e.toString());
                }
                numb_cycles--;
                if (numb_cycles == 0) {
                    Log.d(TAG3, "numb_cycles == 0, exiting");
                    break;
                }
            }
            Log.e(TAG3, "Exiting worker");
            MainActivity.this.iNumbWorkers.decrementAndGet();
        }
        public void cancel(){
            Log.e(TAG3, "Canceling worker");
            doWork.set(false);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        doStopThreads(null);
    }

    public void doStopThreads(View view) {
        //do in this order or HB may start another worker
        stopHB();
        stopWorker();
    }

    private void stopHB(){
        if (hbThread != null){
            hbThread.cancel();
            try {
                hbThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            hbThread = null;
        }
    }

    private void stopWorker(){
        if (wThread != null){
            wThread.cancel();
            try {
                wThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            wThread = null;
        }
    }

    public void do_start(View view) {
        stopHB();
        hbThread = new HB_Thread(this, cb);
    }
}
