package com.example.imgaeclone;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.widget.FrameLayout;

import com.example.imgaeclone.databinding.ActivityMainBinding;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.io.File;

public class MainActivity extends AppCompatActivity {
    private FrameLayout container;

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
        System.loadLibrary("opencv_java4");
    }

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        container = findViewById(R.id.fragment_container);
    }

    @Override
    public void onResume() {
        super.onResume();

        Runnable fullscreenRunnable=new Runnable(){
            @Override
            public void run() {
                WindowInsetsControllerCompat controller = ViewCompat.getWindowInsetsController(container);
                controller.hide(WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        };
        container.postDelayed(fullscreenRunnable, 500);
    }

    public static File getOutputDirectory(Context context)  {
        File[] mediaDir = context.getExternalFilesDirs(Environment.DIRECTORY_PICTURES);
        if (mediaDir.length>0) {
            File destDir = new File(mediaDir[0], context.getResources().getString(R.string.app_name));
            if (!destDir.exists()) {
                destDir.mkdirs();
            }
            return destDir;
        }
        else {
            return context.getApplicationContext().getFilesDir();
        }
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();
}