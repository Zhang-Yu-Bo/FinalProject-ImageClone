package com.example.imgaeclone.fragments;

import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.imgaeclone.ExposureFusion;
import com.example.imgaeclone.MainActivity;
import com.example.imgaeclone.R;

import org.jetbrains.annotations.NotNull;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


public class ResultFragment extends Fragment {

    Mat result = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_result, container, false);
    }

    @Override
    public void onViewCreated(@NotNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ImageView imageView = view.findViewById(R.id.result_img_view);
        String[] mediaPath = ResultFragmentArgs.fromBundle(getArguments()).getMedia();

        ProgressDialog dialog = ProgressDialog.show(getContext(), "", "Computing. Please wait...", true);

        GenerateResult(mediaPath, new Handler(Looper.getMainLooper()){
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                if (msg.getData().getString("status").equals("success")) {
                    dialog.cancel();
                    result = ExposureFusion.getResult();
                    imageView.setImageBitmap(matToBitmap(result));
                }
            }
        });


        view.findViewById(R.id.result_back_button).setOnClickListener(v -> Navigation.findNavController(requireActivity(), R.id.fragment_container).navigateUp());

        view.findViewById(R.id.result_done_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //File resultFile = CameraFragment.createFile(MainActivity.getOutputDirectory(getContext()));
                File resultFile = CameraFragment.createFile(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES));
                Imgproc.cvtColor(result, result, Imgproc.COLOR_RGB2BGRA);
                Imgcodecs.imwrite(resultFile.getAbsolutePath(), result);
                Navigation.findNavController(ResultFragment.this.requireActivity(), R.id.fragment_container)
                        .navigate(ResultFragmentDirections.actionResultToCamera());
                Toast.makeText(getContext(), "Save at: "+resultFile.getAbsolutePath(), Toast.LENGTH_SHORT).show();

                // remove temp file
                resultFile = MainActivity.getOutputDirectory(getContext());
                if (resultFile.isDirectory()) {
                    String[] children = resultFile.list();
                    if (children != null) {
                        for (String i : children) {
                            new File(resultFile, i).delete();
                        }
                    }
                }
            }
        });
    }

    private Bitmap matToBitmap(Mat mat) {
        Bitmap bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(mat, bitmap);
        return bitmap;
    }

    private void GenerateResult(String[] mediaPath, Handler handler) {
        List<Mat> mats = new ArrayList<>();
        for (int i = 0; i < mediaPath.length; i++) {
            Mat mat = Imgcodecs.imread(mediaPath[i]);
            Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2BGRA);
            Bitmap bitmap = matToBitmap(mat);
            Utils.bitmapToMat(bitmap, mat);
            Imgproc.resize(mat, mat, new Size(mat.width() / 4, mat.height() / 4));
            mats.add(mat);
        }
        ExposureFusion.Init(mats, 1, 1, 1, 1, 0);
        ExposureFusion.Process(handler);
    }
}
