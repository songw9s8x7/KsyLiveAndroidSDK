package com.ksy.recordlib.service.recoder;

import android.content.Context;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.SurfaceView;

import com.ksy.recordlib.service.core.KsyMediaSource;
import com.ksy.recordlib.service.core.KsyRecordClient;
import com.ksy.recordlib.service.core.KsyRecordClientConfig;
import com.ksy.recordlib.service.util.Constants;
import com.ksy.recordlib.service.util.FileUtil;
import com.ksy.recordlib.service.util.PrefUtil;

import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by eflakemac on 15/6/19.
 */
public class RecoderVideoSource extends KsyMediaSource implements MediaRecorder.OnInfoListener, MediaRecorder.OnErrorListener {
    private final KsyRecordClient.RecordHandler mHandler;
    private final Context mContext;
    private Camera mCamera;
    private SurfaceView mSurefaceView;
    private MediaRecorder mRecorder;
    private KsyRecordClientConfig mConfig;
    private ParcelFileDescriptor[] piple;
    private FileInputStream is;
    private boolean mRunning = false;
    private String path;

    public RecoderVideoSource(Camera mCamera, KsyRecordClientConfig mConfig, SurfaceView mSurfaceView, KsyRecordClient.RecordHandler mRecordHandler, Context mContext) {
        this.mCamera = mCamera;
        this.mConfig = mConfig;
        this.mSurefaceView = mSurfaceView;
        mRecorder = new MediaRecorder();
        mHandler = mRecordHandler;
        this.mContext = mContext;
    }

    @Override
    public void prepare() {
        mRecorder.setCamera(mCamera);
        mRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
//        CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
//        profile.videoFrameWidth = 1;
//        profile.videoFrameHeight = 1;
//        mRecorder.setProfile(profile);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
//        mRecorder.setVideoFrameRate(mConfig.getVideoFrameRate());
//        mRecorder.setOutputFile(FileUtil.getOutputMediaFile(Constants.MEDIA_TYPE_VIDEO));
        try {
            this.piple = ParcelFileDescriptor.createPipe();
        } catch (IOException e) {
            e.printStackTrace();
            release();
        }
        mRecorder.setOutputFile(this.piple[1].getFileDescriptor());
        try {
            mRecorder.setOnInfoListener(this);
            mRecorder.setOnErrorListener(this);
            mRecorder.prepare();
            mRecorder.start();
        } catch (IOException e) {
            e.printStackTrace();
            release();
        }
    }

    @Override
    public void start() {
        if (!mRunning) {
            mRunning = true;
            this.thread = new Thread(this);
            this.thread.start();
        }
    }

    @Override
    public void stop() {
        if (mRunning == true) {
            release();
        }
    }

    @Override
    public void release() {
        mRunning = false;
        releaseRecorder();
        releaseCamera();
    }

    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
    }

    private void releaseRecorder() {
        if (mRecorder != null) {
            mRecorder.setOnErrorListener(null);
            mRecorder.setOnInfoListener(null);
            mRecorder.reset();
            Log.d(Constants.LOG_TAG, "mRecorder reset");
            mRecorder.release();
            Log.d(Constants.LOG_TAG, "mRecorder release");
            mRecorder = null;
            Log.d(Constants.LOG_TAG, "mRecorder complete");
            mCamera.lock();
        }
    }

    @Override
    public void run() {
        prepare();
        is = new FileInputStream(this.piple[0].getFileDescriptor());
        while (mRunning) {
            Log.d(Constants.LOG_TAG, "entering loop");
            long duration = 0, oldtime = 0;

            // This will skip the MPEG4 header if this step fails we can't stream anything :(
            try {
                byte buffer[] = new byte[4];
                // Skip all atoms preceding mdat atom
                while (true) {
                    while (is.read() != 'm') ;
                    is.read(buffer, 0, 3);
                    if (buffer[0] == 'd' && buffer[1] == 'a' && buffer[2] == 't') break;
                }
            } catch (IOException e) {
                Log.e(Constants.LOG_TAG, "Couldn't skip mp4 header :/");
                return;
            }
            String pl = PrefUtil.getMp4ConfigProfileLevel(mContext);
            String pps = PrefUtil.getMp4ConfigPps(mContext);
            String sps = PrefUtil.getMp4ConfigSps(mContext);
            Log.d(Constants.LOG_TAG, "pl = " + pl + ",pps =" + pps + ",sps = " + sps);
         /*   try {
                while (true) {
                    Log.d(Constants.LOG_TAG, "read =" + is.read());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }*/
        }
        Log.d(Constants.LOG_TAG, "exiting loop");

    }

    public void createFile(String path, byte[] content) {
        try {
            FileOutputStream outputStream = new FileOutputStream(path);
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream);
            bufferedOutputStream.write(content);
            outputStream.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void onInfo(MediaRecorder mr, int what, int extra) {
        Log.d(Constants.LOG_TAG, "onInfo Message what = " + what + ",extra =" + extra);
    }

    @Override
    public void onError(MediaRecorder mr, int what, int extra) {
        Log.d(Constants.LOG_TAG, "onError Message what = " + what + ",extra =" + extra);
    }
}
