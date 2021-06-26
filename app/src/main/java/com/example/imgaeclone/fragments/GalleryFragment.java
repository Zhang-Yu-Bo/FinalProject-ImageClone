package com.example.imgaeclone.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.navigation.Navigation;
import androidx.viewpager.widget.ViewPager;


import com.bumptech.glide.Glide;
import com.example.imgaeclone.R;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


public class GalleryFragment extends Fragment {

    private List<File> mediaList = new ArrayList<>();

    class MediaPagerAdapter extends FragmentStatePagerAdapter {
        public MediaPagerAdapter(@NonNull @NotNull FragmentManager fm) {
            super(fm);
        }

        @NonNull
        @NotNull
        @Override
        public Fragment getItem(int position) {
            return PhotoFragment.create(mediaList.get(position));
        }

        @Override
        public int getCount() {
            return mediaList.size();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean retainInstance = true;

        String[] filePaths = GalleryFragmentArgs.fromBundle(getArguments()).getMedias();
        for (String filePath : filePaths) {
            Log.d("DEBUG: ", filePath);
            mediaList.add(new File(filePath));
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_gallery, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ViewPager mediaViewPager = (ViewPager) view.findViewById(R.id.photo_view_pager);
        if (mediaViewPager != null) {
            mediaViewPager.setOffscreenPageLimit(2);
            mediaViewPager.setAdapter(new MediaPagerAdapter(getChildFragmentManager()));
        }

//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
//            // Use extension method to pad "inside" view containing UI using display cutout's bounds
//            view.findViewById<ConstraintLayout>(R.id.cutout_safe_area).padWithDisplayCutout()
//        }
        view.findViewById(R.id.back_button).setOnClickListener(v -> {
            Navigation.findNavController(requireActivity(), R.id.fragment_container).navigateUp();
        });

    }
}
