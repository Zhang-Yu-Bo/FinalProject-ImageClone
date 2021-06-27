package com.example.imgaeclone.fragments;

import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;
import com.example.imgaeclone.ExposureFusion;
import com.example.imgaeclone.MainActivity;
import com.example.imgaeclone.R;

import org.jetbrains.annotations.NotNull;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


public class ResultFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_result, container, false);
    }

    @Override
    public void onViewCreated(@NotNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ImageView imageView = view.findViewById(R.id.result_img_view);

        Bitmap[] bitmaps = ResultFragmentArgs.fromBundle(getArguments()).getMedia();
        ProgressDialog dialog = ProgressDialog.show(getContext(), "", "Computing. Please wait...", true);

        Mat mat = new Mat();
        Utils.bitmapToMat(bitmaps[0], mat);

//        Glide.with(view).load(mat).into(imageView);
        GenerateResult(bitmaps, new Handler(Looper.getMainLooper()){
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                if (msg.getData().getString("status").equals("success")) {
                    dialog.cancel();
                    Mat result = ExposureFusion.getResult();
                    imageView.setImageBitmap(matToBitmap(result));
//                    File resultFile = CameraFragment.createFile(MainActivity.getOutputDirectory(getContext()));
//                    Imgcodecs.imwrite(resultFile.getAbsolutePath(), result);
//                    Glide.with(view).load(result).into(imageView);
                }
            }
        });

        view.findViewById(R.id.result_back_button).setOnClickListener(v -> Navigation.findNavController(requireActivity(), R.id.fragment_container).navigateUp());

        view.findViewById(R.id.result_done_button).setOnClickListener(v -> Navigation.findNavController(requireActivity(), R.id.fragment_container)
                .navigate(ResultFragmentDirections.actionResultToCamera()));
    }

    private Bitmap matToBitmap(Mat mat) {

        Bitmap bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(mat, bitmap);
        return bitmap;
    }

    private void GenerateResult(Bitmap[] bitmaps, Handler handler) {
        List<Mat> mats = new ArrayList<>();
        for (Bitmap bitmap: bitmaps) {
            Mat mat = new Mat();
            Utils.bitmapToMat(bitmap, mat);
            //Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2BGRA);
            mats.add(mat);
        }
        ExposureFusion.Init(mats, 1, 1, 1, 1, 0);
        ExposureFusion.Process(handler);
    }
}
