/*===============================================================================
Copyright (c) 2016 PTC Inc. All Rights Reserved.


Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/

package app.ar.vr.com.myarvrapp.CloudRecognition;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.RelativeLayout;

import com.google.gson.Gson;
import com.vuforia.CameraDevice;
import com.vuforia.ObjectTracker;
import com.vuforia.State;
import com.vuforia.TargetFinder;
import com.vuforia.TargetSearchResult;
import com.vuforia.Trackable;
import com.vuforia.Tracker;
import com.vuforia.TrackerManager;
import com.vuforia.Vuforia;

import java.util.Vector;

import app.ar.vr.com.myarvrapp.CloudRecognition.VideoPlay.VideoMetaDataModel;
import app.ar.vr.com.myarvrapp.CloudRecognition.VideoPlay.VideoPlayerHelper;
import app.ar.vr.com.myarvrapp.R;
import app.ar.vr.com.myarvrapp.SampleAppMenu.SampleAppMenu;
import app.ar.vr.com.myarvrapp.SampleAppMenu.SampleAppMenuGroup;
import app.ar.vr.com.myarvrapp.SampleAppMenu.SampleAppMenuInterface;
import app.ar.vr.com.myarvrapp.SampleApplication.SampleApplicationControl;
import app.ar.vr.com.myarvrapp.SampleApplication.SampleApplicationException;
import app.ar.vr.com.myarvrapp.SampleApplication.SampleApplicationSession;
import app.ar.vr.com.myarvrapp.SampleApplication.utils.LoadingDialogHandler;
import app.ar.vr.com.myarvrapp.SampleApplication.utils.SampleApplicationGLView;
import app.ar.vr.com.myarvrapp.SampleApplication.utils.Texture;


// The main activity for the CloudReco sample.
public class CloudReco extends Activity implements SampleApplicationControl,
        SampleAppMenuInterface {
    private static final String LOGTAG = "CloudReco";

    private SampleApplicationSession vuforiaAppSession;
    // These codes match the ones defined in TargetFinder in Vuforia.jar
    static final int INIT_SUCCESS = 2;
    static final int INIT_ERROR_NO_NETWORK_CONNECTION = -1;
    static final int INIT_ERROR_SERVICE_NOT_AVAILABLE = -2;
    static final int UPDATE_ERROR_AUTHORIZATION_FAILED = -1;
    static final int UPDATE_ERROR_PROJECT_SUSPENDED = -2;
    static final int UPDATE_ERROR_NO_NETWORK_CONNECTION = -3;
    static final int UPDATE_ERROR_SERVICE_NOT_AVAILABLE = -4;
    static final int UPDATE_ERROR_BAD_FRAME_QUALITY = -5;
    static final int UPDATE_ERROR_UPDATE_SDK = -6;
    static final int UPDATE_ERROR_TIMESTAMP_OUT_OF_RANGE = -7;
    static final int UPDATE_ERROR_REQUEST_TIMEOUT = -8;

    static final int HIDE_LOADING_DIALOG = 0;
    static final int SHOW_LOADING_DIALOG = 1;

    // Our OpenGL view:
    private SampleApplicationGLView mGlView;

    // Our renderer:
    private VideoPlayRenderer mRenderer;

    private SampleAppMenu mSampleAppMenu;

    private boolean mExtendedTracking = false;
    private boolean mFinderStarted = false;
    private boolean mStopFinderIfStarted = false;

    // The textures we will use for rendering:
    private Vector<Texture> mTextures;

    private static final String kAccessKey = "9f1589a795e4f87fe55ba63b27d477beb41ba078";
    private static final String kSecretKey = "d3e2826215e32f60b698cc697d840f28cc224f11";

    // View overlays to be displayed in the Augmented View
    private RelativeLayout mUILayout;

    // Error message handling:
    private int mlastErrorCode = 0;
    private int mInitErrorCode = 0;
    private boolean mFinishActivityOnError;

    // Alert Dialog used to display SDK errors
    private AlertDialog mErrorDialog;

    private GestureDetector mGestureDetector;

    private LoadingDialogHandler loadingDialogHandler = new LoadingDialogHandler(
            this);

    // declare scan line and its animation
    private View scanLine;
    private TranslateAnimation scanAnimation;

    private double mLastErrorTime;

    private boolean mIsDroidDevice = false;

    // A boolean to indicate whether we come from full screen:
    private boolean mReturningFromFullScreen = false;

    private int mSeekPosition;
    private boolean mWasPlaying;
    private String mMovieName;
    private VideoPlayerHelper mVideoPlayerHelper;

    private Activity mActivity;
    private boolean mPlayFullscreenVideo = false;
/*
    boolean mIsStereo = false;
    boolean mIsVR = false;*/

    // Called when the activity first starts or needs to be recreated after
    // resuming the application or a configuration change.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(LOGTAG, "onCreate");
        super.onCreate(savedInstanceState);

        vuforiaAppSession = new SampleApplicationSession(this);

        mActivity = this;
        startLoadingAnimation();

        vuforiaAppSession
                .initAR(this, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // Creates the GestureDetector listener for processing double tap
        mGestureDetector = new GestureDetector(this, new GestureListener());

        mTextures = new Vector<Texture>();
        loadTextures();

        mIsDroidDevice = android.os.Build.MODEL.toLowerCase().startsWith(
                "droid");

        mVideoPlayerHelper = new VideoPlayerHelper();
        mVideoPlayerHelper.init();
        mVideoPlayerHelper.setActivity(this);
        mMovieName = "https://sendvid.com/n1uet4bg.mp4";

    }

    // Process Single Tap event to trigger autofocus
    private class GestureListener extends
            GestureDetector.SimpleOnGestureListener {
        // Used to set autofocus one second after a manual focus is triggered
        private final Handler autofocusHandler = new Handler();


        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }


        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            // Generates a Handler to trigger autofocus
            // after 1 second
            autofocusHandler.postDelayed(new Runnable() {
                public void run() {
                    boolean result = CameraDevice.getInstance().setFocusMode(
                            CameraDevice.FOCUS_MODE.FOCUS_MODE_TRIGGERAUTO);

                    if (!result)
                        Log.e("SingleTapUp", "Unable to trigger focus");
                }
            }, 1000L);

            return true;
        }
    }


    // We want to load specific textures from the APK, which we will later use
    // for rendering.
    private void loadTextures() {

        mTextures.add(Texture.loadTextureFromApk("VideoPlayback/VuforiaSizzleReel_1.png",
                getAssets()));
        mTextures.add(Texture.loadTextureFromApk("VideoPlayback/busy.png",
                getAssets()));
    }


    // Called when the activity will start interacting with the user.
    @Override
    protected void onResume() {
        Log.d(LOGTAG, "onResume");
        super.onResume();

        // This is needed for some Droid devices to force portrait
        if (mIsDroidDevice) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        try {
            vuforiaAppSession.resumeAR();
        } catch (SampleApplicationException e) {
            Log.e(LOGTAG, e.getString());
        }

        // Resume the GL view:
        if (mGlView != null) {
            mGlView.setVisibility(View.VISIBLE);
            mGlView.onResume();
        }


        // Reload all the movies
       /* if (mRenderer != null) {
            if (!mReturningFromFullScreen) {
                mRenderer.requestLoad(mMovieName, mSeekPosition,
                        true);
            } else {
                mRenderer.requestLoad(mMovieName, mSeekPosition,
                        mWasPlaying);
            }
        }*/

        mReturningFromFullScreen = false;
    }


    // Callback for configuration changes the activity handles itself
    @Override
    public void onConfigurationChanged(Configuration config) {
        Log.d(LOGTAG, "onConfigurationChanged");
        super.onConfigurationChanged(config);

        vuforiaAppSession.onConfigurationChanged();
    }

    // Called when returning from the full screen player
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1) {

            mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

            if (resultCode == RESULT_OK) {
                // The following values are used to indicate the position in
                // which the video was being played and whether it was being
                // played or not:
                String movieBeingPlayed = data.getStringExtra("movieName");
                mReturningFromFullScreen = true;

                // Find the movie that was being played full screen
                if (movieBeingPlayed.compareTo(mMovieName) == 0) {
                    mSeekPosition = data.getIntExtra(
                            "currentSeekPosition", 0);
                    mWasPlaying = false;
                }

            }
        }
    }

    // Called when the system is about to start resuming a previous activity.
    @Override
    protected void onPause() {
        Log.d(LOGTAG, "onPause");
        super.onPause();

        // Pauses the OpenGLView
        if (mGlView != null) {
            mGlView.setVisibility(View.INVISIBLE);
            mGlView.onPause();
        }

        // Store the playback state of the movies and unload them:

        // If the activity is paused we need to store the position in which
        // this was currently playing:
        if (mVideoPlayerHelper.isPlayableOnTexture()) {
            mSeekPosition = mVideoPlayerHelper.getCurrentPosition();
            mWasPlaying = mVideoPlayerHelper.getStatus() == VideoPlayerHelper.MEDIA_STATE.PLAYING ? true
                    : false;
        }

        // We also need to release the resources used by the helper, though
        // we don't need to destroy it:
        if (mVideoPlayerHelper != null)
            mVideoPlayerHelper.unload();

        mReturningFromFullScreen = false;

        try {
            vuforiaAppSession.pauseAR();
        } catch (SampleApplicationException e) {
            Log.e(LOGTAG, e.getString());
        }
    }


    // The final call you receive before your activity is destroyed.
    @Override
    protected void onDestroy() {
        Log.d(LOGTAG, "onDestroy");
        super.onDestroy();

        // If the activity is destroyed we need to release all resources:
        if (mVideoPlayerHelper != null)
            mVideoPlayerHelper.deinit();
        mVideoPlayerHelper = null;

        try {
            vuforiaAppSession.stopAR();
        } catch (SampleApplicationException e) {
            Log.e(LOGTAG, e.getString());
        }

        // Unload texture:
        mTextures.clear();
        mTextures = null;

        System.gc();
    }


    private void pauseVideo() {
        if (mVideoPlayerHelper.isPlayableOnTexture()) {
            // If it is playing then we pause it
            mVideoPlayerHelper.pause();
        }
    }

    // Do not exit immediately and instead show the startup screen
    public void onBackPressed() {
        pauseVideo();
        super.onBackPressed();
    }

    public void deinitCloudReco() {
        // Get the object tracker:
        TrackerManager trackerManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) trackerManager
                .getTracker(ObjectTracker.getClassType());
        if (objectTracker == null) {
            Log.e(LOGTAG,
                    "Failed to destroy the tracking data set because the ObjectTracker has not"
                            + " been initialized.");
            return;
        }

        // Deinitialize target finder:
        TargetFinder finder = objectTracker.getTargetFinder();
        finder.deinit();
    }


    private void startLoadingAnimation() {
        // Inflates the Overlay Layout to be displayed above the Camera View
        LayoutInflater inflater = LayoutInflater.from(this);
        mUILayout = (RelativeLayout) inflater.inflate(R.layout.camera_overlay_with_scanline,
                null, false);

        mUILayout.setVisibility(View.VISIBLE);
        mUILayout.setBackgroundColor(Color.BLACK);

        // By default
        loadingDialogHandler.mLoadingDialogContainer = mUILayout
                .findViewById(R.id.loading_indicator);
        loadingDialogHandler.mLoadingDialogContainer
                .setVisibility(View.VISIBLE);

        scanLine = mUILayout.findViewById(R.id.scan_line);
        scanLine.setVisibility(View.GONE);
        scanAnimation = new TranslateAnimation(
                TranslateAnimation.ABSOLUTE, 0f,
                TranslateAnimation.ABSOLUTE, 0f,
                TranslateAnimation.RELATIVE_TO_PARENT, 0f,
                TranslateAnimation.RELATIVE_TO_PARENT, 1.0f);
        scanAnimation.setDuration(4000);
        scanAnimation.setRepeatCount(-1);
        scanAnimation.setRepeatMode(Animation.REVERSE);
        scanAnimation.setInterpolator(new LinearInterpolator());

        addContentView(mUILayout, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
    }


    // Initializes AR application components.
    private void initApplicationAR() {
        // Create OpenGL ES view:
        int depthSize = 16;
        int stencilSize = 0;

        /*mIsStereo = true;

        int deviceMode = Device.MODE.MODE_AR;
        mIsVR = false;

        Device device = Device.getInstance();
        device.setViewerActive(mIsStereo); // Indicates if the app will be using a viewer, stereo mode and initializes the rendering primitives
        device.setMode(deviceMode); // Select if we will be in AR or VR mode*/

        boolean translucent = Vuforia.requiresAlpha();

        // Initialize the GLView with proper flags
        mGlView = new SampleApplicationGLView(this);
        mGlView.init(translucent, depthSize, stencilSize);

        // Setups the Renderer of the GLView
        mRenderer = new VideoPlayRenderer(this, vuforiaAppSession);
        mRenderer.setTextures(mTextures);

        // The renderer comes has the OpenGL context, thus, loading to texture
        // must happen when the surface has been created. This means that we
        // can't load the movie from this thread (GUI) but instead we must
        // tell the GL thread to load it once the surface has been created.

        Log.d("niranjan123 ", "initializing app AR");
        mRenderer.setVideoPlayerHelper(mVideoPlayerHelper);
       // mRenderer.requestLoad(mMovieName, 0, true);

        mGlView.setRenderer(mRenderer);

        float[] temp = {0f, 0f, 0f};
        mRenderer.targetPositiveDimensions.setData(temp);
        mRenderer.videoPlaybackTextureID = -1;
    }


    // Returns the error message for each error code
    private String getStatusDescString(int code) {
        if (code == UPDATE_ERROR_AUTHORIZATION_FAILED)
            return getString(R.string.UPDATE_ERROR_AUTHORIZATION_FAILED_DESC);
        if (code == UPDATE_ERROR_PROJECT_SUSPENDED)
            return getString(R.string.UPDATE_ERROR_PROJECT_SUSPENDED_DESC);
        if (code == UPDATE_ERROR_NO_NETWORK_CONNECTION)
            return getString(R.string.UPDATE_ERROR_NO_NETWORK_CONNECTION_DESC);
        if (code == UPDATE_ERROR_SERVICE_NOT_AVAILABLE)
            return getString(R.string.UPDATE_ERROR_SERVICE_NOT_AVAILABLE_DESC);
        if (code == UPDATE_ERROR_UPDATE_SDK)
            return getString(R.string.UPDATE_ERROR_UPDATE_SDK_DESC);
        if (code == UPDATE_ERROR_TIMESTAMP_OUT_OF_RANGE)
            return getString(R.string.UPDATE_ERROR_TIMESTAMP_OUT_OF_RANGE_DESC);
        if (code == UPDATE_ERROR_REQUEST_TIMEOUT)
            return getString(R.string.UPDATE_ERROR_REQUEST_TIMEOUT_DESC);
        if (code == UPDATE_ERROR_BAD_FRAME_QUALITY)
            return getString(R.string.UPDATE_ERROR_BAD_FRAME_QUALITY_DESC);
        else {
            return getString(R.string.UPDATE_ERROR_UNKNOWN_DESC);
        }
    }


    // Returns the error message for each error code
    private String getStatusTitleString(int code) {
        if (code == UPDATE_ERROR_AUTHORIZATION_FAILED)
            return getString(R.string.UPDATE_ERROR_AUTHORIZATION_FAILED_TITLE);
        if (code == UPDATE_ERROR_PROJECT_SUSPENDED)
            return getString(R.string.UPDATE_ERROR_PROJECT_SUSPENDED_TITLE);
        if (code == UPDATE_ERROR_NO_NETWORK_CONNECTION)
            return getString(R.string.UPDATE_ERROR_NO_NETWORK_CONNECTION_TITLE);
        if (code == UPDATE_ERROR_SERVICE_NOT_AVAILABLE)
            return getString(R.string.UPDATE_ERROR_SERVICE_NOT_AVAILABLE_TITLE);
        if (code == UPDATE_ERROR_UPDATE_SDK)
            return getString(R.string.UPDATE_ERROR_UPDATE_SDK_TITLE);
        if (code == UPDATE_ERROR_TIMESTAMP_OUT_OF_RANGE)
            return getString(R.string.UPDATE_ERROR_TIMESTAMP_OUT_OF_RANGE_TITLE);
        if (code == UPDATE_ERROR_REQUEST_TIMEOUT)
            return getString(R.string.UPDATE_ERROR_REQUEST_TIMEOUT_TITLE);
        if (code == UPDATE_ERROR_BAD_FRAME_QUALITY)
            return getString(R.string.UPDATE_ERROR_BAD_FRAME_QUALITY_TITLE);
        else {
            return getString(R.string.UPDATE_ERROR_UNKNOWN_TITLE);
        }
    }


    // Shows error messages as System dialogs
    public void showErrorMessage(int errorCode, double errorTime, boolean finishActivityOnError) {
        if (errorTime < (mLastErrorTime + 5.0) || errorCode == mlastErrorCode)
            return;

        mlastErrorCode = errorCode;
        mFinishActivityOnError = finishActivityOnError;

        runOnUiThread(new Runnable() {
            public void run() {
                if (mErrorDialog != null) {
                    mErrorDialog.dismiss();
                }

                // Generates an Alert Dialog to show the error message
                AlertDialog.Builder builder = new AlertDialog.Builder(
                        CloudReco.this);
                builder
                        .setMessage(
                                getStatusDescString(CloudReco.this.mlastErrorCode))
                        .setTitle(
                                getStatusTitleString(CloudReco.this.mlastErrorCode))
                        .setCancelable(false)
                        .setIcon(0)
                        .setPositiveButton(getString(R.string.button_OK),
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        if (mFinishActivityOnError) {
                                            finish();
                                        } else {
                                            dialog.dismiss();
                                        }
                                    }
                                });

                mErrorDialog = builder.create();
                mErrorDialog.show();
            }
        });
    }


    // Shows initialization error messages as System dialogs
    public void showInitializationErrorMessage(String message) {
        final String errorMessage = message;
        runOnUiThread(new Runnable() {
            public void run() {
                if (mErrorDialog != null) {
                    mErrorDialog.dismiss();
                }

                // Generates an Alert Dialog to show the error message
                AlertDialog.Builder builder = new AlertDialog.Builder(
                        CloudReco.this);
                builder
                        .setMessage(errorMessage)
                        .setTitle(getString(R.string.INIT_ERROR))
                        .setCancelable(false)
                        .setIcon(0)
                        .setPositiveButton(getString(R.string.button_OK),
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        finish();
                                    }
                                });

                mErrorDialog = builder.create();
                mErrorDialog.show();
            }
        });
    }


    public void startFinderIfStopped() {

        Log.d(LOGTAG, "starting finder if stopped ");
        if (!mFinderStarted) {
            mFinderStarted = true;

            // Get the object tracker:
            TrackerManager trackerManager = TrackerManager.getInstance();
            ObjectTracker objectTracker = (ObjectTracker) trackerManager
                    .getTracker(ObjectTracker.getClassType());

            // Initialize target finder:
            TargetFinder targetFinder = objectTracker.getTargetFinder();

            targetFinder.clearTrackables();

            Log.d(LOGTAG, "started object recognition startFinderIfStopped" + targetFinder.startRecognition());
            scanlineStart();
        }
    }


    public void stopFinderIfStarted() {
        if (mFinderStarted) {
            mFinderStarted = false;

            // Get the object tracker:
            TrackerManager trackerManager = TrackerManager.getInstance();
            ObjectTracker objectTracker = (ObjectTracker) trackerManager
                    .getTracker(ObjectTracker.getClassType());

            // Initialize target finder:
            TargetFinder targetFinder = objectTracker.getTargetFinder();

            targetFinder.stop();
            scanlineStop();
        }
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Process the Gestures
        if (mSampleAppMenu != null && mSampleAppMenu.processEvent(event))
            return true;

        return mGestureDetector.onTouchEvent(event);
    }


    @Override
    public boolean doLoadTrackersData() {
        Log.d(LOGTAG, "initCloudReco");

        // Get the object tracker:
        TrackerManager trackerManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) trackerManager
                .getTracker(ObjectTracker.getClassType());

        // Initialize target finder:
        TargetFinder targetFinder = objectTracker.getTargetFinder();

        // Start initialization:
        if (targetFinder.startInit(kAccessKey, kSecretKey)) {
            targetFinder.waitUntilInitFinished();
        }

        int resultCode = targetFinder.getInitState();
        if (resultCode != TargetFinder.INIT_SUCCESS) {
            if (resultCode == TargetFinder.INIT_ERROR_NO_NETWORK_CONNECTION) {
                mInitErrorCode = UPDATE_ERROR_NO_NETWORK_CONNECTION;
            } else {
                mInitErrorCode = UPDATE_ERROR_SERVICE_NOT_AVAILABLE;
            }

            Log.e(LOGTAG, "Failed to initialize target finder.");
            return false;
        }

        return true;
    }


    @Override
    public boolean doUnloadTrackersData() {
        return true;
    }


    @Override
    public void onInitARDone(SampleApplicationException exception) {

        if (exception == null) {
            initApplicationAR();

            mRenderer.setActive(true);

            // Now add the GL surface view. It is important
            // that the OpenGL ES surface view gets added
            // BEFORE the camera is started and video
            // background is configured.
            addContentView(mGlView, new LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT));

            // Start the camera:
            try {
                vuforiaAppSession.startAR(CameraDevice.CAMERA_DIRECTION.CAMERA_DIRECTION_DEFAULT);
            } catch (SampleApplicationException e) {
                Log.e(LOGTAG, e.getString());
            }

            boolean result = CameraDevice.getInstance().setFocusMode(
                    CameraDevice.FOCUS_MODE.FOCUS_MODE_CONTINUOUSAUTO);

            if (!result)
                Log.e(LOGTAG, "Unable to enable continuous autofocus");

            mUILayout.bringToFront();

            // Hides the Loading Dialog
            loadingDialogHandler.sendEmptyMessage(HIDE_LOADING_DIALOG);

            mUILayout.setBackgroundColor(Color.TRANSPARENT);

            mSampleAppMenu = new SampleAppMenu(this, this, "Cloud Reco",
                    mGlView, mUILayout, null);
            setSampleAppMenuSettings();

        } else {
            Log.e(LOGTAG, exception.getString());
            if (mInitErrorCode != 0) {
                showErrorMessage(mInitErrorCode, 10, true);
            } else {
                showInitializationErrorMessage(exception.getString());
            }
        }
        Log.d(LOGTAG, "onARInitDone");
    }


    @Override
    public void onVuforiaUpdate(State state) {

        Log.d(LOGTAG, "onVuforia update");
        // Get the tracker manager:
        TrackerManager trackerManager = TrackerManager.getInstance();

        // Get the object tracker:
        ObjectTracker objectTracker = (ObjectTracker) trackerManager
                .getTracker(ObjectTracker.getClassType());

        // Get the target finder:
        TargetFinder finder = objectTracker.getTargetFinder();

        // Check if there are new results available:
        final int statusCode = finder.updateSearchResults();

        // Show a message if we encountered an error:
        if (statusCode < 0) {

            boolean closeAppAfterError = (
                    statusCode == UPDATE_ERROR_NO_NETWORK_CONNECTION ||
                            statusCode == UPDATE_ERROR_SERVICE_NOT_AVAILABLE);

            showErrorMessage(statusCode, state.getFrame().getTimeStamp(), closeAppAfterError);

        } else if (statusCode == TargetFinder.UPDATE_RESULTS_AVAILABLE) {
            Log.d(LOGTAG, "Target available");
            // Process new search results
            if (finder.getResultCount() > 0) {
                TargetSearchResult result = finder.getResult(0);
                Gson gson = new Gson();
                VideoMetaDataModel model = gson.fromJson(result.getMetaData(), VideoMetaDataModel.class);
                String txt = model == null ? "null" : model.url + " " + model.title;
                Log.d("niranjan1624", " target found " + result.getUniqueTargetId() + " " + txt);

                if (model != null) {
                    //play video from url
                    mRenderer.unload();
                    mRenderer.requestLoad(model.url,0, true);
                    mVideoPlayerHelper.load(model.url,
                            VideoPlayerHelper.MEDIA_TYPE.ON_TEXTURE_FULLSCREEN, true, 0);
                    scanlineStop();

                }
                // Check if this target is suitable for tracking:
                if (result.getTrackingRating() > 0) {
                    Log.d("niranjan123 ", "target available target rating is " + result.getTrackingRating());

                    Trackable trackable = finder.enableTracking(result);
                    if (mExtendedTracking)
                        trackable.startExtendedTracking();
                }
            }
        } else {

            Log.d(LOGTAG, "Target not available " + statusCode + " | " + finder.getResultCount());
        }
    }


    @Override
    public boolean doInitTrackers() {
        TrackerManager tManager = TrackerManager.getInstance();
        Tracker tracker;

        // Indicate if the trackers were initialized correctly
        boolean result = true;

        tracker = tManager.initTracker(ObjectTracker.getClassType());
        if (tracker == null) {
            Log.e(
                    LOGTAG,
                    "Tracker not initialized. Tracker already initialized or the camera is already started");
            result = false;
        } else {
            Log.i(LOGTAG, "Tracker successfully initialized");
        }
/*
        RotationalDeviceTracker deviceTracker = (RotationalDeviceTracker) tManager.initTracker(RotationalDeviceTracker.getClassType());
        if (deviceTracker == null) {
            Log.e(
                    LOGTAG,
                    "Rotational Device Tracker not initialized. Tracker already initialized or the camera is already started");
            result = false;
        } else {
            // Set correction model to head if using viewer otherwise handheld
            deviceTracker.setModelCorrection(deviceTracker.getDefaultHeadModel());

            TransformModel model = deviceTracker.getModelCorrection();
            if (model == null) {
                Log.e(LOGTAG, "Error no transform model");
            } else {
                // We check which transform model is set for the rotation correction and then we get the pivot used to apply the correction when the device rotates. This is done so we know the model was set properly.

                // This transform model is set when there is a viewer present, we use a head model for this correction
                if (model.getType() == TransformModel.TYPE.TRANSFORM_MODEL_HEAD) {
                    Log.i(LOGTAG, "Transform model: Head");
                    HeadTransformModel headModel = (HeadTransformModel) model;
                    Log.i(LOGTAG, "Transform model pivot: " + headModel.getPivotPoint().getData()[0] + "," + headModel.getPivotPoint().getData()[1] + "," + headModel.getPivotPoint().getData()[2]);
                }

                // In this mode we are in full screen mode and the user is holding the device with the hands so we apply a handheld model
                if (model.getType() == TransformModel.TYPE.TRANSFORM_MODEL_HANDHELD) {
                    Log.i(LOGTAG, "Transform model: Handheld");
                    HandheldTransformModel handheldModel = (HandheldTransformModel) model;
                    Log.i(LOGTAG, "Transform model pivot: " + handheldModel.getPivotPoint().getData()[0] + "," + handheldModel.getPivotPoint().getData()[1] + "," + handheldModel.getPivotPoint().getData()[2]);
                }
            }

            Log.i(LOGTAG, "Rotational Device Tracker successfully initialized");
        }*/
        return result;
    }


    @Override
    public boolean doStartTrackers() {

        Log.d(LOGTAG, "do start trackers");
        // Indicate if the trackers were started correctly
        boolean result = true;

        // Start the tracker:
        TrackerManager trackerManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) trackerManager
                .getTracker(ObjectTracker.getClassType());
        boolean start = objectTracker.start();


        Log.d(LOGTAG, "started object tracking " + start);
        // Start cloud based recognition if we are in scanning mode:
        TargetFinder targetFinder = objectTracker.getTargetFinder();
        boolean reco = targetFinder.startRecognition();


        Log.d(LOGTAG, "started recognizing " + reco);
        scanlineStart();
        mFinderStarted = true;

        return result;
    }


    @Override
    public boolean doStopTrackers() {
        // Indicate if the trackers were stopped correctly
        boolean result = true;

        TrackerManager trackerManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) trackerManager
                .getTracker(ObjectTracker.getClassType());


        Log.d(LOGTAG, "trying to stop tracking" + objectTracker);
        if (objectTracker != null) {
            objectTracker.stop();

            // Stop cloud based recognition:
            TargetFinder targetFinder = objectTracker.getTargetFinder();
            Log.d(LOGTAG, "target finder stopped " + targetFinder.stop());
            scanlineStop();
            mFinderStarted = false;

            // Clears the trackables
            targetFinder.clearTrackables();
        } else {
            result = false;
        }

        return result;
    }


    @Override
    public boolean doDeinitTrackers() {
        // Indicate if the trackers were deinitialized correctly
        boolean result = true;

        TrackerManager tManager = TrackerManager.getInstance();
        tManager.deinitTracker(ObjectTracker.getClassType());

        return result;
    }

    final public static int CMD_BACK = -1;
    final public static int CMD_EXTENDED_TRACKING = 1;
    final private static int CMD_FULLSCREEN_VIDEO = 1;

    // This method sets the menu's settings

    private void setSampleAppMenuSettings() {
        SampleAppMenuGroup group;

        group = mSampleAppMenu.addGroup("", false);
        group.addTextItem(getString(R.string.menu_back), -1);

        group = mSampleAppMenu.addGroup("", true);
        group.addSelectionItem(getString(R.string.menu_extended_tracking),
                CMD_EXTENDED_TRACKING, false);

        mSampleAppMenu.attachMenu();
    }


    @Override
    public boolean menuProcess(int command) {
        boolean result = true;

        switch (command) {
            case CMD_BACK:
                finish();
                break;

            case CMD_EXTENDED_TRACKING:
                TrackerManager trackerManager = TrackerManager.getInstance();
                ObjectTracker objectTracker = (ObjectTracker) trackerManager
                        .getTracker(ObjectTracker.getClassType());

                TargetFinder targetFinder = objectTracker.getTargetFinder();

                if (targetFinder.getNumImageTargets() == 0) {
                    result = true;
                }

                for (int tIdx = 0; tIdx < targetFinder.getNumImageTargets(); tIdx++) {
                    Trackable trackable = targetFinder.getImageTarget(tIdx);

                    if (!mExtendedTracking) {
                        if (!trackable.startExtendedTracking()) {
                            Log.e(LOGTAG,
                                    "Failed to start extended tracking target");
                            result = false;
                        } else {
                            Log.d(LOGTAG,
                                    "Successfully started extended tracking target");
                        }
                    } else {
                        if (!trackable.stopExtendedTracking()) {
                            Log.e(LOGTAG,
                                    "Failed to stop extended tracking target");
                            result = false;
                        } else {
                            Log.d(LOGTAG,
                                    "Successfully started extended tracking target");
                        }
                    }
                }

                if (result)
                    mExtendedTracking = !mExtendedTracking;

                break;
           /* case CMD_FULLSCREEN_VIDEO:
                mPlayFullscreenVideo = !mPlayFullscreenVideo;


                    if (mVideoPlayerHelper.getStatus() == VideoPlayerHelper.MEDIA_STATE.PLAYING)
                    {
                        // If it is playing then we pause it
                        mVideoPlayerHelper.pause();

                        mVideoPlayerHelper.play(true,
                                mSeekPosition);
                    }

                break;*/


        }

        return result;
    }
    public boolean isScanning = true;
    public void scanlineStart() {
        Log.d("1624578", "start_isScanning " + isScanning);
        if(!isScanning) {
            this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    scanLine.setVisibility(View.VISIBLE);
                    scanLine.setAnimation(scanAnimation);
                }
            });
        }
        isScanning = true;
    }

    public void scanlineStop() {
        Log.d("1624578", "stop_isScanning " + isScanning);
        if(isScanning) {
            this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    scanLine.setVisibility(View.GONE);
                    scanLine.clearAnimation();
                }
            });
        }
        isScanning = false;

    }

}
