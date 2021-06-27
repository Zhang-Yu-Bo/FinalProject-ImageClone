package com.example.imgaeclone.fragments;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.viewpager.widget.ViewPager;

import com.bumptech.glide.Glide;
import com.example.imgaeclone.R;

import java.io.File;

public class ResultFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_result, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ImageView imageView = (ImageView)view.findViewById(R.id.result_img_view);

        // File resource = new File();
        Bitmap image = ResultFragmentArgs.fromBundle(getArguments()).getMedia();
        Glide.with(view).load(image).into(imageView);

        view.findViewById(R.id.result_back_button).setOnClickListener(v -> {
            Navigation.findNavController(requireActivity(), R.id.fragment_container).navigateUp();
        });

        view.findViewById(R.id.result_done_button).setOnClickListener(v -> {
            Navigation.findNavController(requireActivity(), R.id.fragment_container)
                    .navigate(ResultFragmentDirections.actionResultToCamera());
        });
    }
}
