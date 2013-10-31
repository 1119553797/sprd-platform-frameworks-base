
package android.hardware.BoardScore;

import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.Binder;
import android.os.SystemProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import android.os.RemoteException;
import android.util.Slog;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;

import java.io.FileOutputStream;
import java.io.IOException;

public class BoardScoreService extends IBoardScoreService.Stub {

    private static final String TAG = "BoardScore";
    private ScoreThread mScoreworkingThread;
    private Context mContext;
    List<String> softwareList = new ArrayList<String>();


    public BoardScoreService(Context context) {
        mContext = context;
        getSoftwareList();
        mScoreworkingThread = new ScoreThread();
        new Thread(mScoreworkingThread).start();

    }

    public void StartBoardScoreService() {
        return;
    }

    public void getSoftwareList() {
        softwareList.add("antutu");
        softwareList.add("benchmark");
        softwareList.add("ludashi");
        softwareList.add("cfbench");
        softwareList.add("quicinc.vellamo");
        softwareList.add("geekbench");
        softwareList.add("greenecomputing.linpack");
        softwareList.add("nenamark");
        softwareList.add("performance.test");
        softwareList.add("QuadrantStandard");
        softwareList.add("farproc.wifi.analyzer");
    }

    public void setSoftwareList(List<String> softwareList) {
        this.softwareList = softwareList;
    }

    class ScoreThread implements Runnable {
        private String psname;
        // speed up: bSPeeding = true
        private boolean bSpeeding = false;
        // find a board score softeware in runing process
        private boolean beFind = false;

        public ScoreThread() {

        }

        public void run() {
            do {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                }
                ActivityManager _ActivityManager = (ActivityManager) mContext
                        .getSystemService(Context.ACTIVITY_SERVICE);
                List<RunningTaskInfo> list = _ActivityManager.getRunningTasks(1);
                int i = list.size();
                beFind = false;
                for (int j = 0; j < list.size(); j++) {
                    psname = list.get(j).baseActivity.toString();
                    for (int n = 0; n < softwareList.size(); n++) {
                        String pkname = softwareList.get(n);
                        if (psname.contains(pkname)) {
                            beFind = true;
                            break;
                        }
                    }
                    if (beFind) {
                        break;
                    }
                }
                if (beFind) {
                    if (!bSpeeding) {
                        // Slog.i("BoardScoreService", "Need to speed up!");
                        SystemProperties.set("ctl.start", "inputfreq");
                        // Slog.i("BoardScoreService","speed up ok!");
                        bSpeeding = true;
                    }
                }
                else
                {
                    if (bSpeeding) {
                        // Slog.i("BoardScoreService", "Need to speed down!");
                        SystemProperties.set("ctl.stop", "inputfreq");
                        SystemProperties.set("ctl.start", "recoveryfreq");
                        // Slog.i("BoardScoreService", "speed down ok!");
                        bSpeeding = false;
                    }
                }

            } while (true);
        }
    }

}
