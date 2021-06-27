package com.example.imgaeclone.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.fragment.app.Fragment;

import com.example.imgaeclone.R;

import java.io.File;
import com.bumptech.glide.Glide;

public class PhotoFragment extends Fragment {
    private static final String FILE_NAME_KEY = "file_name";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return new ImageView(getContext());
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState){
        super.onViewCreated(view, savedInstanceState);
        Bundle args = getArguments();
        if (args.getString(FILE_NAME_KEY, "") != "") {
            File resource = new File(args.getString(FILE_NAME_KEY));
            Glide.with(view).load(resource).into((ImageView)view);
        }
    }


    static Fragment create(String path){
        PhotoFragment photoFragment = new PhotoFragment();
        Bundle bundle = new Bundle();
        bundle.putString(FILE_NAME_KEY, path);
        photoFragment.setArguments(bundle);
        return photoFragment;
    }

}
