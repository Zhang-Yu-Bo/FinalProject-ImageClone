package com.example.imgaeclone.fragments;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.hardware.display.DisplayManager;
import android.icu.text.SimpleDateFormat;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.Range;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExposureState;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.imgaeclone.MainActivity;
import com.example.imgaeclone.R;
import com.google.common.util.concurrent.ListenableFuture;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static androidx.navigation.fragment.NavHostFragment.findNavController;

public class CameraFragment extends Fragment {

    private final static String TAG = "CameraXBasic";
    private final static String FILENAME = "yyyy-MM-dd-HH-mm-ss-SSS";
    private final static String PHOTO_EXTENSION = ".jpg";
    private final static double RATIO_4_3_VALUE = 4.0 / 3.0;
    private final static double RATIO_16_9_VALUE = 16.0 / 9.0;

    private ConstraintLayout container;
    private PreviewView viewFinder;
    private File outputDirectory;
    private String[] imagePaths;

    private int displayId = -1;
    private int lensFacing = CameraSelector.LENS_FACING_BACK;
    private Preview preview = null;
    private ImageCapture imageCapture = null;
    private ImageAnalysis imageAnalyzer = null;
    private Camera camera = null;
    private ProcessCameraProvider cameraProvider = null;
    private WindowManager windowManager;

    private DisplayManager displayManager;
    private ExecutorService cameraExecutor;

    private int exposureTimeTimes;
    private int needPictureCount = 3;
    private int middleExposureTimeIndex;
    private int[] exposureTimeIndexes = {-6, 0, 6};

    @Override
    public void onResume() {
        super.onResume();
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            findNavController(this).navigate(R.id.action_camera_to_permissions);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cameraExecutor.shutdown();
//        displayManager.unregisterDisplayListener();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
        return inflater.inflate(R.layout.fragment_camera, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        container = (ConstraintLayout)view;
        viewFinder = container.findViewById(R.id.view_finder);
        cameraExecutor = Executors.newSingleThreadExecutor();

        displayManager = (DisplayManager) requireContext().getSystemService(Context.DISPLAY_SERVICE);
        outputDirectory = MainActivity.getOutputDirectory(requireContext());

        viewFinder.post(() -> {
            displayId = viewFinder.getDisplay().getDisplayId();
            updateCameraUi();
            setUpCamera();
        });
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        bindCameraUseCases();
    }

    private void setUpCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(getContext());
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();

                if (!cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)){
                    if (cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                        lensFacing = CameraSelector.LENS_FACING_FRONT;
                    }
                }
            } catch (ExecutionException | CameraInfoUnavailableException | InterruptedException e) {
                Log.e(TAG, "set up camera failed", e);
            }
            bindCameraUseCases();
        },  ContextCompat.getMainExecutor(requireContext()));
    }

    private void  bindCameraUseCases(){
        int screenAspectRatio = AspectRatio.RATIO_16_9;
        if (windowManager != null) {
            Rect metrics = windowManager.getCurrentWindowMetrics().getBounds();
            screenAspectRatio = aspectRatio(metrics.width(), metrics.height());
        }
        int rotation = viewFinder.getDisplay().getRotation();
        CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(lensFacing).build();

        // Preview
        preview = new Preview.Builder()
                .setTargetAspectRatio(screenAspectRatio)
                .setTargetRotation(rotation)
                .build();
        preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(rotation)
                .build();
        try {
            cameraProvider.unbindAll();
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);//, imageAnalyzer);
        } catch (Exception exc) {
            Log.e(TAG, "Use case binding failed", exc);
        }
    }

    private int aspectRatio(int width, int height) {
        int maxLen = width > height ? width : height;
        int minLen = width < height ? width : height;
        Double previewRatio = Double.valueOf(maxLen) / minLen;
        if (Math.abs(previewRatio - RATIO_4_3_VALUE) <= Math.abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3;
        }
        return AspectRatio.RATIO_16_9;
    }

    @SuppressLint("UnsafeOptInUsageError")
    private void takePictures(int times) {
        camera.getCameraControl().setExposureCompensationIndex(exposureTimeIndexes[times - 1]);
        File photoFile = createFile(outputDirectory, FILENAME, PHOTO_EXTENSION);
        ImageCapture.Metadata metadata = new ImageCapture.Metadata();
        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile)
                .setMetadata(metadata)
                .build();
        imageCapture.takePicture(outputOptions, cameraExecutor, new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull @NotNull ImageCapture.OutputFileResults output) {
                Uri savedUri = output.getSavedUri();
                if (savedUri == null) savedUri = Uri.fromFile(photoFile);
                Log.d(TAG, "Photo capture succeeded: " + savedUri);

                String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension("jpg");

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    requireActivity().sendBroadcast(
                            new Intent(android.hardware.Camera.ACTION_NEW_PICTURE, savedUri)
                    );
                }
                MediaScannerConnection.scanFile(getContext(), new String[]{savedUri.toString()}, new String[]{mimeType}, (path, uri) -> {
                    Log.i("ExternalStorage", "Scanned " + path);
                });
                imagePaths[times - 1] = photoFile.getAbsolutePath();
                if (times == 1) {
                    container.postDelayed(
                            () -> {
                                findNavController(getParentFragment()).navigate(
                                        CameraFragmentDirections.actionCameraToGallery(imagePaths)
                                );
                            }
                            , 200);
                }
                else{
                    takePictures(times - 1);
                }
            }

            @Override
            public void onError(@NonNull @NotNull ImageCaptureException exception) {
                Log.e(TAG, "Photo capture failed: ", exception);
            }
        });
        // We can only change the foreground Drawable using API level 23+ API
        container.postDelayed(
                () -> {
                    container.setForeground(new ColorDrawable(Color.BLACK));
                    container.postDelayed(() -> container.setForeground(null), 50);
                }
                , 500);
    }

    private void updateCameraUi() {
        ConstraintLayout uiLayout = container.findViewById(R.id.camera_ui_container);
        if (uiLayout != null) {
            container.removeView(uiLayout);
        }

        View controls = View.inflate(requireContext(), R.layout.camera_ui_container, container);

        controls.findViewById(R.id.camera_capture_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // need set exposureTimeIndexes
                // ExposureState exposureState = camera.getCameraInfo().getExposureState();
                // if (!exposureState.isExposureCompensationSupported()) return;
                // Range<Integer> range = exposureState.getExposureCompensationRange();
                imagePaths = new String[needPictureCount];
                takePictures(needPictureCount);
            }
        });
    }

    @SuppressLint({"NewApi", "LocalSuppress"})
    private File createFile(File baseFolder, String format, String extension) {
        SimpleDateFormat dateFormat = new SimpleDateFormat(format, java.util.Locale.getDefault());
        return new File(baseFolder, dateFormat.format(System.currentTimeMillis()) + extension);
    }

}
