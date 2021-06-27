package com.example.imgaeclone.fragments;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.imgaeclone.R;

import static androidx.navigation.fragment.NavHostFragment.findNavController;

public class PermissionsFragment extends Fragment {

    static final int PERMISSIONS_REQUEST_CODE = 10445;
    static final String[] PERMISSIONS_REQUIRED = {
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!hasPermissions(requireContext())) {
            requestPermissions(PERMISSIONS_REQUIRED, PERMISSIONS_REQUEST_CODE);
        } else {
            navigateToCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    navigateToCamera();
                }
                return;
        }
    }

    private void navigateToCamera() {
        // Navigation.findNavController(requireActivity(), R.id.fragment_container)
        findNavController(this)
                .navigate(R.id.action_permissions_to_camera);
    }

    static public boolean hasPermissions(Context context) {
        for(String requiredPermission: PERMISSIONS_REQUIRED) {
            int check = ContextCompat.checkSelfPermission(context, requiredPermission);
            if (check != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }
}
