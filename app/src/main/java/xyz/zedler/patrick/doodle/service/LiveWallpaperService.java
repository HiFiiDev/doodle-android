/*
 * This file is part of Doodle Android.
 *
 * Doodle Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Doodle Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Doodle Android. If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright (c) 2020-2021 by Patrick Zedler
 */

package xyz.zedler.patrick.doodle.service;

import android.animation.ValueAnimator;
import android.app.KeyguardManager;
import android.app.WallpaperColors;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources.NotFoundException;
import android.content.res.Resources.Theme;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.SystemClock;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowManager;
import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import java.util.Random;
import xyz.zedler.patrick.doodle.Constants;
import xyz.zedler.patrick.doodle.Constants.DEF;
import xyz.zedler.patrick.doodle.Constants.PREF;
import xyz.zedler.patrick.doodle.Constants.USER_PRESENCE;
import xyz.zedler.patrick.doodle.Constants.VARIANT;
import xyz.zedler.patrick.doodle.Constants.WALLPAPER;
import xyz.zedler.patrick.doodle.R;
import xyz.zedler.patrick.doodle.drawable.SvgDrawable;
import xyz.zedler.patrick.doodle.util.MigrationUtil;
import xyz.zedler.patrick.doodle.util.PrefsUtil;

public class LiveWallpaperService extends WallpaperService {

  private final static String TAG = LiveWallpaperService.class.getSimpleName();

  private SharedPreferences sharedPrefs;
  private Theme theme;
  private String wallpaper, variant;
  private boolean nightMode, followSystem, isNight;
  private int colorBackground;
  private int parallax;
  private float size;
  private float fps;
  private int zoomIntensity;
  private boolean isZoomLauncherEnabled, isZoomUnlockEnabled;
  private String presence;
  private boolean isReceiverRegistered = false;
  private UserPresenceListener userPresenceListener;
  private SvgDrawable svgDrawable;

  private VectorDrawable doodleArc;
  private VectorDrawable doodleDot;
  private VectorDrawable doodleU;
  private VectorDrawable doodleRect;
  private VectorDrawable doodleRing;
  private VectorDrawable doodleMoon;
  private VectorDrawable doodlePoly;

  private VectorDrawable neonKidneyFront;
  private VectorDrawable neonCircleFront;
  private VectorDrawable neonPill;
  private VectorDrawable neonLine;
  private VectorDrawable neonKidneyBack;
  private VectorDrawable neonCircleBack;
  private VectorDrawable neonDot;

  private VectorDrawable geometricRect;
  private VectorDrawable geometricLine;
  private VectorDrawable geometricPoly;
  private VectorDrawable geometricCircle;
  private VectorDrawable geometricSheet;

  private float zDoodleArc;
  private float zDoodleDot;
  private float zDoodleU;
  private float zDoodleRect;
  private float zDoodleRing;
  private float zDoodleMoon;
  private float zDoodlePoly;

  private float zNeonKidneyFront;
  private float zNeonCircleFront;
  private float zNeonPill;
  private float zNeonLine;
  private float zNeonKidneyBack;
  private float zNeonCircleBack;
  private float zNeonDot;

  private float zGeometricRect;
  private float zGeometricLine;
  private float zGeometricPoly;
  private float zGeometricCircle;
  private float zGeometricSheet;

  private final BroadcastReceiver presenceReceiver = new BroadcastReceiver() {
    public void onReceive(Context context, Intent intent) {
      switch (intent.getAction()) {
        case Intent.ACTION_USER_PRESENT:
          setUserPresence(USER_PRESENCE.UNLOCKED);
          break;
        case Intent.ACTION_SCREEN_OFF:
          setUserPresence(USER_PRESENCE.OFF);
          break;
        case Intent.ACTION_SCREEN_ON:
          setUserPresence(isKeyguardLocked() ? USER_PRESENCE.LOCKED : USER_PRESENCE.UNLOCKED);
          break;
      }
    }
  };

  @Override
  public Engine onCreateEngine() {
    sharedPrefs = new PrefsUtil(this).getSharedPrefs();
    new MigrationUtil(sharedPrefs).checkForMigrations();

    fps = getFrameRate();

    registerReceiver();
    setUserPresence(isKeyguardLocked() ? USER_PRESENCE.LOCKED : USER_PRESENCE.UNLOCKED);

    return new UserAwareEngine();
  }

  public void onDestroy() {
    unregisterReceiver();
    clearShapes();
    super.onDestroy();
  }

  private void registerReceiver() {
    if (!isReceiverRegistered) {
      IntentFilter filter = new IntentFilter(Intent.ACTION_USER_PRESENT);
      filter.addAction(Intent.ACTION_SCREEN_OFF);
      filter.addAction(Intent.ACTION_SCREEN_ON);
      registerReceiver(presenceReceiver, filter);
      isReceiverRegistered = true;
    }
  }

  private void unregisterReceiver() {
    if (isReceiverRegistered) {
      unregisterReceiver(presenceReceiver);
      isReceiverRegistered = false;
    }
  }

  private void refreshTheme() {
    if (isNightMode()) {
      switch (wallpaper) {
        case WALLPAPER.PIXEL:
          theme.applyStyle(R.style.Wallpaper_Doodle_Night, true);
          colorBackground = getCompatColor(R.color.wp_bg_doodle_night);
          break;
        case WALLPAPER.REIKO:
          theme.applyStyle(R.style.Wallpaper_Neon_Night, true);
          colorBackground = getCompatColor(R.color.wp_bg_neon_night);
          break;
        case WALLPAPER.ANTHONY:
          theme.applyStyle(R.style.Wallpaper_Geometric_Night, true);
          colorBackground = getCompatColor(R.color.wp_bg_geometric_night);
          break;
      }
    } else {
      switch (wallpaper) {
        case WALLPAPER.PIXEL:
          switch (variant) {
            case Constants.VARIANT.BLACK:
              theme.applyStyle(R.style.Wallpaper_Doodle_Black, true);
              colorBackground = getCompatColor(R.color.wp_bg_doodle_black);
              break;
            case Constants.VARIANT.WHITE:
              theme.applyStyle(R.style.Wallpaper_Doodle_White, true);
              colorBackground = getCompatColor(R.color.wp_bg_doodle_white);
              break;
            case Constants.VARIANT.ORANGE:
              theme.applyStyle(R.style.Wallpaper_Doodle_Orange, true);
              colorBackground = getCompatColor(R.color.wp_bg_doodle_orange);
              break;
          }
          break;
        case WALLPAPER.REIKO:
          theme.applyStyle(R.style.Wallpaper_Neon, true);
          colorBackground = getCompatColor(R.color.wp_bg_neon);
          break;
        case WALLPAPER.ANTHONY:
          theme.applyStyle(R.style.Wallpaper_Geometric, true);
          colorBackground = getCompatColor(R.color.wp_bg_geometric);
          break;
      }
    }

    switch (wallpaper) {
      case WALLPAPER.PIXEL:
        doodleArc = getVectorDrawable(R.drawable.doodle_shape_arc);
        doodleDot = getVectorDrawable(R.drawable.doodle_shape_dot);
        doodleU = getVectorDrawable(R.drawable.doodle_shape_u);
        doodleRect = getVectorDrawable(R.drawable.doodle_shape_rect);
        doodleRing = getVectorDrawable(R.drawable.doodle_shape_ring);
        doodleMoon = getVectorDrawable(R.drawable.doodle_shape_moon);
        doodlePoly = getVectorDrawable(R.drawable.doodle_shape_poly);
        break;
      case WALLPAPER.REIKO:
        neonKidneyFront = getVectorDrawable(R.drawable.neon_shape_kidney_front);
        neonCircleFront = getVectorDrawable(R.drawable.neon_shape_circle_front);
        neonPill = getVectorDrawable(R.drawable.neon_shape_pill);
        neonLine = getVectorDrawable(R.drawable.neon_shape_line);
        neonKidneyBack = getVectorDrawable(R.drawable.neon_shape_kidney_back);
        neonCircleBack = getVectorDrawable(R.drawable.neon_shape_circle_back);
        neonDot = getVectorDrawable(R.drawable.neon_shape_dot);
        break;
      case WALLPAPER.ANTHONY:
        geometricRect = getVectorDrawable(R.drawable.geometric_shape_rect);
        geometricLine = getVectorDrawable(R.drawable.geometric_shape_line);
        geometricPoly = getVectorDrawable(R.drawable.geometric_shape_poly);
        geometricCircle = getVectorDrawable(R.drawable.geometric_shape_circle);
        geometricSheet = getVectorDrawable(R.drawable.geometric_shape_sheet);
        break;
    }

    svgDrawable = new SvgDrawable(this, R.raw.doodle);
  }

  private void newRandomZ() {
    Random random = new Random();
    switch (wallpaper) {
      case WALLPAPER.PIXEL:
        zDoodleArc = getRandomFloat(random);
        zDoodleDot = getRandomFloat(random);
        zDoodleU = getRandomFloat(random);
        zDoodleRect = getRandomFloat(random);
        zDoodleRing = getRandomFloat(random);
        zDoodleMoon = getRandomFloat(random);
        zDoodlePoly = getRandomFloat(random);
        break;
      case WALLPAPER.REIKO:
        zNeonKidneyFront = getRandomFloat(random);
        zNeonCircleFront = getRandomFloat(random);
        zNeonPill = getRandomFloat(random);
        zNeonLine = getRandomFloat(random);
        zNeonKidneyBack = getRandomFloat(random);
        zNeonCircleBack = getRandomFloat(random);
        zNeonDot = getRandomFloat(random);
        break;
      case WALLPAPER.ANTHONY:
        zGeometricRect = getRandomFloat(random);
        zGeometricLine = getRandomFloat(random);
        zGeometricPoly = getRandomFloat(random);
        zGeometricCircle = getRandomFloat(random);
        zGeometricSheet = getRandomFloat(random);
        break;
    }
  }

  private float getRandomFloat(Random random) {
    float min = 0.1f;
    float max = 1;
    return min + random.nextFloat() * (max - min);
  }

  private void clearShapes() {
    doodleArc = null;
    doodleDot = null;
    doodleU = null;
    doodleRect = null;
    doodleRing = null;
    doodleMoon = null;
    doodlePoly = null;

    neonKidneyFront = null;
    neonCircleFront = null;
    neonPill = null;
    neonLine = null;
    neonKidneyBack = null;
    neonCircleBack = null;
    neonDot = null;

    geometricRect = null;
    geometricLine = null;
    geometricPoly = null;
    geometricCircle = null;
    geometricSheet = null;
  }

  private boolean isNightMode() {
    if (nightMode && !followSystem) {
      return true;
    }
    int flags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
    return nightMode && flags == Configuration.UI_MODE_NIGHT_YES;
  }

  private boolean isPortrait() {
    return getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
  }

  private boolean isKeyguardLocked() {
    return ((KeyguardManager) getSystemService(KEYGUARD_SERVICE)).isKeyguardLocked();
  }

  private int getCompatColor(@ColorRes int resId) {
    return ContextCompat.getColor(this, resId);
  }

  private float getFrameRate() {
    WindowManager windowManager = ((WindowManager) getSystemService(Context.WINDOW_SERVICE));
    return windowManager != null ? windowManager.getDefaultDisplay().getRefreshRate() : 60;
  }

  private VectorDrawable getVectorDrawable(@DrawableRes int resId) {
    Drawable drawable = null;
    try {
      drawable = ResourcesCompat.getDrawable(getResources(), resId, theme);
    } catch (NotFoundException e) {
      Log.e(TAG, "getVectorDrawable: " + e);
    }
    return drawable != null ? (VectorDrawable) drawable.mutate() : null;
  }

  public void setUserPresence(final String presence) {
    if (presence.equals(this.presence)) {
      return;
    }
    this.presence = presence;
    if (userPresenceListener != null) {
      userPresenceListener.onPresenceChange(presence);
    }
  }

  public interface UserPresenceListener {

    void onPresenceChange(String presence);
  }

  // ENGINE ------------------------------------------------------------

  class UserAwareEngine extends Engine implements UserPresenceListener {
    private float xOffset = 0;
    private float zoomLauncher = 0;
    private float zoomUnlock = 0;
    private long lastDraw;
    private boolean isVisible;
    private ValueAnimator valueAnimator;

    @Override
    public void onCreate(SurfaceHolder surfaceHolder) {
      super.onCreate(surfaceHolder);

      userPresenceListener = this;

      loadSettings();
      loadTheme();

      zoomUnlock = 1;
      animateZoom(0);

      setTouchEventsEnabled(false);
    }

    @Override
    public void onDestroy() {
      if (valueAnimator != null) {
        valueAnimator.cancel();
        valueAnimator.removeAllUpdateListeners();
        valueAnimator = null;
      }
    }

    @Override
    public WallpaperColors onComputeColors() {
      int background = 0xFF232323;
      if (isNightMode()) {
        switch (wallpaper) {
          case WALLPAPER.PIXEL:
            background = 0xFF272628;
            break;
          case WALLPAPER.REIKO:
            background = 0xFF0e032d;
            break;
          case WALLPAPER.ANTHONY:
            background = 0xFF212121;
            break;
        }
      } else {
        switch (wallpaper) {
          case WALLPAPER.PIXEL:
            switch (variant) {
              case VARIANT.BLACK:
                background = 0xFF232323;
                break;
              case VARIANT.WHITE:
                background = 0xFFdbd7ce;
                break;
              case VARIANT.ORANGE:
                background = 0xFFfbb29e;
                break;
            }
            break;
          case WALLPAPER.REIKO:
            background = 0xFFcbcbef;
            break;
          case WALLPAPER.ANTHONY:
            background = 0xFFb9c1c7;
            break;
        }
      }

      if (VERSION.SDK_INT >= VERSION_CODES.O_MR1) {
        return WallpaperColors.fromDrawable(new ColorDrawable(background));
      } else {
        return super.onComputeColors();
      }
    }

    @Override
    public void onVisibilityChanged(boolean visible) {
      isVisible = visible;
      if (!visible) {
        return;
      }

      handleRefreshRequests();

      newRandomZ();
      drawFrame(true);
    }

    private void loadSettings() {
      parallax = sharedPrefs.getInt(PREF.PARALLAX, DEF.PARALLAX);
      zoomIntensity = sharedPrefs.getInt(PREF.ZOOM, DEF.ZOOM);
      isZoomLauncherEnabled = sharedPrefs.getBoolean(PREF.ZOOM_LAUNCHER, DEF.ZOOM_LAUNCHER);
      isZoomUnlockEnabled = sharedPrefs.getBoolean(PREF.ZOOM_UNLOCK, DEF.ZOOM_UNLOCK);

      if (isZoomUnlockEnabled && !isReceiverRegistered) {
        registerReceiver();
      } else if (!isZoomUnlockEnabled && isReceiverRegistered) {
        unregisterReceiver();
      }
    }

    private void loadTheme() {
      wallpaper = sharedPrefs.getString(PREF.WALLPAPER, DEF.WALLPAPER);
      variant = sharedPrefs.getString(PREF.VARIANT, DEF.VARIANT);
      nightMode = sharedPrefs.getBoolean(PREF.NIGHT_MODE, DEF.NIGHT_MODE);
      followSystem = sharedPrefs.getBoolean(PREF.FOLLOW_SYSTEM, DEF.FOLLOW_SYSTEM);
      size = sharedPrefs.getFloat(PREF.SIZE, DEF.SIZE);

      theme = null;
      clearShapes();
      System.gc();

      isNight = isNightMode();
      theme = getResources().newTheme();
      refreshTheme();
      colorsHaveChanged();
    }

    private void handleRefreshRequests() {
      boolean settingsApplied = sharedPrefs.getBoolean(PREF.SETTINGS_APPLIED, true);
      if (!settingsApplied) {
        sharedPrefs.edit().putBoolean(PREF.SETTINGS_APPLIED, true).apply();
        loadSettings();
      }

      boolean themeApplied = sharedPrefs.getBoolean(PREF.THEME_APPLIED, true);
      if (!themeApplied) {
        sharedPrefs.edit().putBoolean(PREF.THEME_APPLIED, true).apply();
        loadTheme();
      } else if (isNight != isNightMode()) {
        loadTheme();
      }
    }

    @Override
    public void onOffsetsChanged(
        float xOffset,
        float yOffset,
        float xStep,
        float yStep,
        int xPixels,
        int yPixels
    ) {
      if (parallax != 0) {
        this.xOffset = xOffset;
        drawFrame(true);
      }
    }

    @Override
    public void onZoomChanged(float zoom) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && zoomIntensity > 0) {
        this.zoomLauncher = zoom;
        drawFrame(false);
      }
    }

    @Override
    public void onPresenceChange(String presence) {
      switch (presence) {
        case USER_PRESENCE.OFF:
          zoomUnlock = 1;
          drawFrame(true);
          break;
        case USER_PRESENCE.LOCKED:
          animateZoom(0.5f);
          break;
        case USER_PRESENCE.UNLOCKED:
          if (isVisible) {
            animateZoom(0);
          } else {
            zoomUnlock = 0;
            drawFrame(true);
          }
          break;
      }
    }

    void drawFrame(boolean force) {
      if (!force && SystemClock.elapsedRealtime() - lastDraw < 1000 / fps) {
        return;
      }
      final SurfaceHolder surfaceHolder = getSurfaceHolder();
      Canvas canvas = null;
      try {
        if (VERSION.SDK_INT >= VERSION_CODES.O) {
          canvas = surfaceHolder.lockHardwareCanvas();
        } else {
          canvas = surfaceHolder.lockCanvas();
        }
        if (canvas != null) {
          canvas.drawColor(colorBackground);

          switch (wallpaper) {
            case WALLPAPER.PIXEL:
              drawShape(doodleArc, 0.25, 0.28, zDoodleArc);
              drawShape(doodleDot, 0.142, 0.468, zDoodleDot);
              drawShape(doodleU, isPortrait() ? 0.25 : 0.32, 0.72, zDoodleU);
              drawShape(doodleRect, 0.7, 0.8, zDoodleRect);
              drawShape(
                  doodleRing, isPortrait() ? 0.66 : 0.6, isPortrait() ? 0.5 : 0.48, zDoodleRing
              );
              drawShape(
                  doodleMoon, isPortrait() ? 0.75 : 0.65, isPortrait() ? 0.56 : 0.58, zDoodleMoon
              );
              drawShape(doodlePoly, 0.7, 0.2, zDoodlePoly);
              drawOnCanvas(
                  canvas,
                  doodleArc, doodleDot, doodleU, doodleRect, doodleRing, doodleMoon, doodlePoly
              );
              break;
            case WALLPAPER.REIKO:
              double shift = isPortrait() ? 0 : -0.15;
              drawShape(neonKidneyFront, 0.85 + shift, 0.65, zNeonKidneyFront);
              drawShape(neonCircleFront, 0.98 + shift, 0.468, zNeonCircleFront);
              drawShape(neonPill, 0.26 + shift, 0.58, zNeonPill);
              drawShape(neonLine, 0.55 + shift, 0.4, zNeonLine);
              drawShape(neonKidneyBack, 0.63 + shift, 0.37, zNeonKidneyBack);
              drawShape(neonCircleBack, 0.5 + shift, 0.63, zNeonCircleBack);
              drawShape(neonDot, 0.6 + shift, 0.15, zNeonDot);
              drawOnCanvas(
                  canvas,
                  neonDot, neonCircleBack, neonKidneyBack, neonLine,
                  neonPill, neonCircleFront, neonKidneyFront
              );
              break;
            case WALLPAPER.ANTHONY:
              drawShape(geometricRect, 0.35, 0.78, zGeometricRect);
              drawShape(geometricLine, 0.5, 0.82, zGeometricLine);
              drawShape(geometricPoly, 0.8, 0.67, zGeometricPoly);
              drawShape(geometricCircle, 0.6, 0.2, zGeometricCircle);
              drawShape(
                  geometricSheet,
                  isPortrait() ? 0.4 : 0.25, 0.21, zGeometricSheet, false
              );
              drawOnCanvas(
                  canvas,
                  geometricSheet, geometricPoly, geometricCircle, geometricLine, geometricRect
              );
              break;
          }

          svgDrawable.draw(canvas);

          lastDraw = SystemClock.elapsedRealtime();
        }
      } finally {
        if (canvas != null) {
          surfaceHolder.unlockCanvasAndPost(canvas);
        }
      }
    }

    private void drawOnCanvas(Canvas canvas, Drawable... drawables) {
      for (Drawable drawable : drawables) {
        if (drawable != null) {
          drawable.draw(canvas);
        }
      }
    }

    private void drawShape(Drawable drawable, double x, double y, double z) {
      drawShape(drawable, x, y, z, true);
    }

    private void drawShape(Drawable drawable, double x, double y, double z, boolean shouldZoom) {
      if (drawable == null) {
        return;
      }

      float intensity = shouldZoom ? zoomIntensity / 10f : 0;

      double finalZoomLauncher = isZoomLauncherEnabled ? zoomLauncher * z * intensity : 0;
      double finalZoomUnlock = isZoomUnlockEnabled ? zoomUnlock * z * intensity : 0;

      double scale = size - finalZoomLauncher - finalZoomUnlock;
      int width = (int) (scale * drawable.getIntrinsicWidth());
      int height = (int) (scale * drawable.getIntrinsicHeight());

      int xPos, yPos, offset;
      Rect frame = getSurfaceHolder().getSurfaceFrame();
      offset = (int) (xOffset * z * (parallax * 100));
      xPos = ((int) (x * frame.width())) - offset;
      yPos = (int) (y * frame.height());

      // zoom out moves shapes to the center
      int centerX = frame.centerX();
      int centerY = frame.centerY();
      if (xPos < centerX) {
        int dist = centerX - xPos;
        if (isZoomLauncherEnabled) {
          xPos += dist * z * zoomLauncher * intensity;
        }
        if (isZoomUnlockEnabled) {
          xPos += dist * z * zoomUnlock * intensity;
        }
      } else {
        int dist = xPos - centerX;
        if (isZoomLauncherEnabled) {
          xPos -= dist * z * zoomLauncher * intensity;
        }
        if (isZoomUnlockEnabled) {
          xPos -= dist * z * zoomUnlock * intensity;
        }
      }
      if (yPos < centerY) {
        int dist = centerY - yPos;
        if (isZoomLauncherEnabled) {
          yPos += dist * z * zoomLauncher * intensity;
        }
        if (isZoomUnlockEnabled) {
          yPos += dist * z * zoomUnlock * intensity;
        }
      } else {
        int dist = yPos - centerY;
        if (isZoomLauncherEnabled) {
          yPos -= dist * z * zoomLauncher * intensity;
        }
        if (isZoomUnlockEnabled) {
          yPos -= dist * z * zoomUnlock * intensity;
        }
      }

      drawable.setBounds(
          xPos - width / 2,
          yPos - height / 2,
          xPos + width / 2,
          yPos + height / 2
      );
    }

    private void animateZoom(float valueTo) {
      if (valueAnimator != null) {
        valueAnimator.cancel();
        valueAnimator.removeAllUpdateListeners();
        valueAnimator = null;
      }
      valueAnimator = ValueAnimator.ofFloat(zoomUnlock, valueTo);
      valueAnimator.addUpdateListener(animation -> {
        zoomUnlock = (float) animation.getAnimatedValue();
        drawFrame(true);
      });
      valueAnimator.setInterpolator(new FastOutSlowInInterpolator());
      valueAnimator.setDuration(1250).start();
    }

    private void colorsHaveChanged() {
      if (VERSION.SDK_INT >= VERSION_CODES.O_MR1) {
        notifyColorsChanged();
        // We have to call it again to take any effect, causes a warning...
        notifyColorsChanged();
      }
    }
  }
}
