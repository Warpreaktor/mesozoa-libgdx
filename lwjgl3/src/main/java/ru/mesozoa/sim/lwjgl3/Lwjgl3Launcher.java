package ru.mesozoa.sim.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Window;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;
import org.lwjgl.glfw.GLFW;
import ru.mesozoa.sim.MesozoaVisualApp;

public final class Lwjgl3Launcher {
    public static void main(String[] args) {
        long seed = readLongArg(args, "--seed", MesozoaVisualApp.RANDOM_SEED);
        float speed = readFloatArg(args, "--speed", 0.35f);

        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Mesozoa Visual Simulator");
        config.setWindowedMode(1920, 1080);
        config.setResizable(true);
        config.useVsync(true);
        config.setForegroundFPS(60);

        config.setWindowListener(new Lwjgl3WindowAdapter() {
            @Override
            public void created(Lwjgl3Window window) {
                long handle = window.getWindowHandle();
                GLFW.glfwShowWindow(handle);
                GLFW.glfwFocusWindow(handle);
                GLFW.glfwRequestWindowAttention(handle);
            }
        });

        new Lwjgl3Application(new MesozoaVisualApp(seed, speed), config);
    }

    private static long readLongArg(String[] args, String name, long fallback) {
        String value = readStringArg(args, name);
        if (value == null) return fallback;

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static float readFloatArg(String[] args, String name, float fallback) {
        String value = readStringArg(args, name);
        if (value == null) return fallback;

        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String readStringArg(String[] args, String name) {
        for (int i = 0; i < args.length - 1; i++) {
            if (name.equals(args[i])) return args[i + 1];
        }

        return null;
    }
}
