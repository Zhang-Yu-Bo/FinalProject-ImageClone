package com.example.imgaeclone.fragments;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.navigation.Navigation;
import androidx.viewpager.widget.ViewPager;


import com.example.imgaeclone.R;

import org.jetbrains.annotations.NotNull;


public class GalleryFragment extends Fragment {

    private Bitmap[] mediaList;
    private String[] filePaths;

    class MediaPagerAdapter extends FragmentStatePagerAdapter {
        public MediaPagerAdapter(@NonNull @NotNull FragmentManager fm) {
            super(fm);
        }

        @NonNull
        @NotNull
        @Override
        public Fragment getItem(int position) {
            return PhotoFragment.create(filePaths[getCount() - position - 1]);
        }

        @Override
        public int getCount() {
            return filePaths.length;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean retainInstance = true;

        filePaths = GalleryFragmentArgs.fromBundle(getArguments()).getMedias();
        mediaList = new Bitmap[filePaths.length];
        int i = 0;
        for (String filePath : filePaths) {
            mediaList[i++] = BitmapFactory.decodeFile(filePath);
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

        view.findViewById(R.id.done_button).setOnClickListener(v -> {
            Navigation.findNavController(requireActivity(), R.id.fragment_container)
                    .navigate(GalleryFragmentDirections.actionGalleryToResult(mediaList));
        });

    }
}
