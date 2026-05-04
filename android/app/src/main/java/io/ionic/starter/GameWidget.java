package io.ionic.starter;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.ComponentName;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GameWidget extends AppWidgetProvider {

    private static final String TAG = "GameWidget";
    private static final String PREFS_NAME = "CapacitorStorage";
    private static final String FAVORITE_KEY = "favoriteGames";

    private static ExecutorService executor;
    private static Handler mainHandler;

    // 🔄 Se ejecuta cuando el widget se actualiza
    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        Log.d(TAG, "Widget actualizándose...");

        if (executor == null) executor = Executors.newSingleThreadExecutor();
        if (mainHandler == null) mainHandler = new Handler(Looper.getMainLooper());

        for (int id : appWidgetIds) {
            updateWidget(context, manager, id);
        }
    }

    // 🚀 Se ejecuta cuando agregas el widget
    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        scheduleUpdate(context);
    }

    // ⏱️ Programar actualización automática
    public static void scheduleUpdate(Context context) {
        Intent intent = new Intent(context, GameWidget.class);
        intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        long interval = 5 * 60 * 1000; // 🔥 cada 5 minutos (estable)

        alarmManager.setInexactRepeating(
                AlarmManager.RTC,
                System.currentTimeMillis(),
                interval,
                pendingIntent
        );
    }

    // 🎮 Actualizar contenido del widget
    private void updateWidget(Context context, AppWidgetManager manager, int widgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.game_widget);

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(FAVORITE_KEY, null);

        if (json == null || json.equals("[]")) {
            views.setTextViewText(R.id.widget_title, "Sin favoritos");
            views.setTextViewText(R.id.widget_price, "");
            manager.updateAppWidget(widgetId, views);
            return;
        }

        try {
            JSONArray favorites = new JSONArray(json);

            // 🔥 Carrusel automático (cambia cada 5 minutos)
            int index = (int) (System.currentTimeMillis() / (2 * 60 * 1000)) % favorites.length();
            JSONObject game = favorites.getJSONObject(index);

            String title = game.optString("title", "Juego");
            String price = game.optString("salePrice", "");
            String thumb = game.optString("thumb", "");

            views.setTextViewText(R.id.widget_title, title);
            views.setTextViewText(R.id.widget_price, "$" + price);

            manager.updateAppWidget(widgetId, views);

            // 📷 Cargar imagen en segundo plano
            executor.execute(() -> {
                Bitmap image = downloadImage(thumb);
                if (image != null) {
                    mainHandler.post(() -> {
                        views.setImageViewBitmap(R.id.widget_image, image);
                        manager.updateAppWidget(widgetId, views);
                    });
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error widget: " + e.getMessage());
            views.setTextViewText(R.id.widget_title, "Error");
            manager.updateAppWidget(widgetId, views);
        }
    }

    // 🌐 Descargar imagen
    private Bitmap downloadImage(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoInput(true);
            conn.connect();
            InputStream input = conn.getInputStream();
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            input.close();
            conn.disconnect();
            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, "Error imagen: " + e.getMessage());
            return null;
        }
    }

    // 🔄 Forzar actualización desde la app
    public static void forceUpdate(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName componentName = new ComponentName(context, GameWidget.class);
        int[] ids = manager.getAppWidgetIds(componentName);

        for (int id : ids) {
            new GameWidget().onUpdate(context, manager, new int[]{id});
        }
    }
}