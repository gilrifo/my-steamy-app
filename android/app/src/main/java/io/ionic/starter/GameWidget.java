package io.ionic.starter;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Widget profesional de My Steamy App
 * Muestra múltiples juegos favoritos en carrusel automático
 */
public class GameWidget extends AppWidgetProvider {
    
    // Constantes
    private static final String PREFS_NAME = "CapacitorStorage";
    private static final String FAVORITE_KEY = "favoriteGames";
    private static final String BASE_URL = "https://www.cheapshark.com/api/1.0";
    private static final long UPDATE_INTERVAL = 8000; // 8 segundos entre juegos
    
    // Datos del carrusel
    private static List<FavoriteGame> favoriteGames = new ArrayList<>();
    private static List<List<DealInfo>> allDeals = new ArrayList<>();
    private static int currentGameIndex = 0;
    private static int currentDealIndex = 0;
    
    // Threading
    private static Handler handler;
    private static Runnable updateRunnable;
    private static ExecutorService executor;
    
    // Cache de imágenes
    private static Bitmap currentGameImage = null;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId);
        }
        startUpdateService(context);
    }

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        if (executor == null) {
            executor = Executors.newSingleThreadExecutor();
        }
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        stopUpdateService();
        if (executor != null) {
            executor.shutdown();
            executor = null;
        }
    }

    /**
     * Actualiza el widget leyendo todos los favoritos
     */
    private void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.game_widget);
        
        // Leer todos los favoritos de CapacitorStorage
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String favoritesJson = prefs.getString(FAVORITE_KEY, null);
        
        if (favoritesJson == null) {
            showEmptyState(views, "No hay favoritos", "Abre la app y selecciona juegos ❤️");
            appWidgetManager.updateAppWidget(appWidgetId, views);
            return;
        }

        try {
            JSONArray favoritesArray = new JSONArray(favoritesJson);
            
            if (favoritesArray.length() == 0) {
                showEmptyState(views, "Lista vacía", "Agrega favoritos desde la app");
                appWidgetManager.updateAppWidget(appWidgetId, views);
                return;
            }

            // Limpiar datos anteriores
            favoriteGames.clear();
            allDeals.clear();
            
            // Procesar cada favorito
            for (int i = 0; i < favoritesArray.length(); i++) {
                JSONObject fav = favoritesArray.getJSONObject(i);
                FavoriteGame game = new FavoriteGame();
                game.gameID = fav.getString("gameID");
                game.title = fav.getString("title");
                game.thumb = fav.getString("thumb");
                game.dealID = fav.optString("dealID", "");
                game.storeID = fav.optString("storeID", "");
                game.salePrice = fav.optString("salePrice", "");
                game.normalPrice = fav.optString("normalPrice", "");
                game.savings = fav.optString("savings", "");
                favoriteGames.add(game);
            }

            // Cargar datos del primer juego
            if (!favoriteGames.isEmpty()) {
                loadGameData(context, 0, views, appWidgetManager, appWidgetId);
            }
            
        } catch (JSONException e) {
            e.printStackTrace();
            showEmptyState(views, "Error", "No se pudieron cargar los favoritos");
            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }

    /**
     * Carga los datos de un juego específico desde la API
     */
    private void loadGameData(Context context, int gameIndex, RemoteViews views, 
                              AppWidgetManager manager, int widgetId) {
        if (gameIndex >= favoriteGames.size()) return;
        
        FavoriteGame game = favoriteGames.get(gameIndex);
        
        executor.execute(() -> {
            try {
                // Descargar imagen del juego
                Bitmap gameImage = downloadBitmap(game.thumb);
                if (gameImage != null) {
                    currentGameImage = gameImage;
                    views.setImageViewBitmap(R.id.widget_game_image, gameImage);
                }

                // Obtener detalles de ofertas
                URL url = new URL(BASE_URL + "/games?id=" + game.gameID);
                String response = makeRequest(url);
                
                JSONObject gameData = new JSONObject(response);
                JSONArray deals = gameData.getJSONArray("deals");
                JSONObject cheapestPriceEver = gameData.optJSONObject("cheapestPriceEver");
                
                // Obtener catálogo de tiendas
                URL storesUrl = new URL(BASE_URL + "/stores");
                String storesResponse = makeRequest(storesUrl);
                JSONArray stores = new JSONArray(storesResponse);
                
                List<DealInfo> gameDeals = new ArrayList<>();
                
                for (int i = 0; i < deals.length(); i++) {
                    JSONObject deal = deals.getJSONObject(i);
                    String storeID = deal.getString("storeID");
                    
                    String storeName = "Unknown";
                    String storeLogo = "";
                    for (int j = 0; j < stores.length(); j++) {
                        JSONObject store = stores.getJSONObject(j);
                        if (store.getString("storeID").equals(storeID)) {
                            storeName = store.getString("storeName");
                            storeLogo = "https://www.cheapshark.com" + 
                                       store.getJSONObject("images").getString("logo");
                            break;
                        }
                    }
                    
                    DealInfo info = new DealInfo();
                    info.storeName = storeName;
                    info.storeLogo = storeLogo;
                    info.price = deal.getString("price");
                    info.retailPrice = deal.getString("retailPrice");
                    info.savings = Math.round(Float.parseFloat(deal.getString("savings"))) + "%";
                    gameDeals.add(info);
                }
                
                // Guardar deals de este juego
                if (gameIndex < allDeals.size()) {
                    allDeals.set(gameIndex, gameDeals);
                } else {
                    allDeals.add(gameDeals);
                }
                
                // Actualizar UI con primer deal del juego
                if (!gameDeals.isEmpty()) {
                    updateGameView(views, game, gameDeals.get(0), cheapestPriceEver);
                    manager.updateAppWidget(widgetId, views);
                }
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Actualiza la vista con datos del juego y deal actual
     */
    private void updateGameView(RemoteViews views, FavoriteGame game, DealInfo deal, 
                                JSONObject cheapestPriceEver) {
        // Título del juego
        views.setTextViewText(R.id.widget_title, game.title);
        
        // Badge de descuento
        if (deal.savings != null && !deal.savings.equals("0%")) {
            views.setTextViewText(R.id.widget_savings_badge, "-" + deal.savings);
            views.setViewVisibility(R.id.widget_savings_badge, View.VISIBLE);
        } else {
            views.setViewVisibility(R.id.widget_savings_badge, View.GONE);
        }
        
        // Info de tienda
        views.setTextViewText(R.id.widget_store, deal.storeName);
        views.setTextViewText(R.id.widget_retail, "$" + deal.retailPrice);
        views.setTextViewText(R.id.widget_price, "$" + deal.price);
        
        // Precio histórico
        if (cheapestPriceEver != null && cheapestPriceEver.has("price")) {
            try {
                String cheapest = cheapestPriceEver.getString("price");
                views.setTextViewText(R.id.widget_cheapest, "$" + cheapest);
            } catch (JSONException e) {
                views.setTextViewText(R.id.widget_cheapest, "N/A");
            }
        } else {
            views.setTextViewText(R.id.widget_cheapest, "N/A");
        }
        
        // Logo de tienda
        executor.execute(() -> {
            Bitmap logo = downloadBitmap(deal.storeLogo);
            if (logo != null) {
                views.setImageViewBitmap(R.id.widget_store_logo, logo);
            }
        });
    }

    /**
     * Muestra estado vacío
     */
    private void showEmptyState(RemoteViews views, String title, String message) {
        views.setTextViewText(R.id.widget_title, title);
        views.setTextViewText(R.id.widget_store, message);
        views.setTextViewText(R.id.widget_price, "");
        views.setTextViewText(R.id.widget_retail, "");
        views.setTextViewText(R.id.widget_savings_badge, "");
        views.setTextViewText(R.id.widget_cheapest, "");
        views.setViewVisibility(R.id.widget_savings_badge, View.GONE);
    }

    /**
     * Inicia el carrusel automático
     */
    private void startUpdateService(Context context) {
        if (handler == null) {
            handler = new Handler(Looper.getMainLooper());
        }
        
        if (updateRunnable == null) {
            updateRunnable = new Runnable() {
                @Override
                public void run() {
                    if (!favoriteGames.isEmpty() && !allDeals.isEmpty()) {
                        
                        // Avanzar al siguiente juego
                        currentGameIndex = (currentGameIndex + 1) % favoriteGames.size();
                        currentDealIndex = 0; // Reset deals del nuevo juego
                        
                        AppWidgetManager manager = AppWidgetManager.getInstance(context);
                        ComponentName component = new ComponentName(context, GameWidget.class);
                        int[] ids = manager.getAppWidgetIds(component);
                        
                        for (int id : ids) {
                            RemoteViews views = new RemoteViews(context.getPackageName(), 
                                                                R.layout.game_widget);
                            
                            FavoriteGame game = favoriteGames.get(currentGameIndex);
                            
                            // Recargar imagen del juego
                            executor.execute(() -> {
                                Bitmap newImage = downloadBitmap(game.thumb);
                                if (newImage != null) {
                                    views.setImageViewBitmap(R.id.widget_game_image, newImage);
                                }
                                
                                // Cargar deals de este juego si no están cacheados
                                if (currentGameIndex < allDeals.size() && 
                                    !allDeals.get(currentGameIndex).isEmpty()) {
                                    
                                    List<DealInfo> deals = allDeals.get(currentGameIndex);
                                    updateGameView(views, game, deals.get(0), null);
                                    manager.updateAppWidget(id, views);
                                } else {
                                    // Recargar desde API
                                    loadGameData(context, currentGameIndex, views, manager, id);
                                }
                            });
                        }
                    }
                    handler.postDelayed(this, UPDATE_INTERVAL);
                }
            };
        }
        
        handler.removeCallbacks(updateRunnable);
        handler.postDelayed(updateRunnable, UPDATE_INTERVAL);
    }

    private void stopUpdateService() {
        if (handler != null && updateRunnable != null) {
            handler.removeCallbacks(updateRunnable);
        }
    }

    // ============================================
    // UTILIDADES
    // ============================================

    private String makeRequest(URL url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        
        InputStream is = connection.getInputStream();
        java.util.Scanner scanner = new java.util.Scanner(is).useDelimiter("\\A");
        String response = scanner.hasNext() ? scanner.next() : "";
        scanner.close();
        connection.disconnect();
        
        return response;
    }

    private Bitmap downloadBitmap(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            input.close();
            connection.disconnect();
            return bitmap;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    // ============================================
    // CLASES INTERNAS
    // ============================================

    private static class FavoriteGame {
        String gameID;
        String title;
        String thumb;
        String dealID;
        String storeID;
        String salePrice;
        String normalPrice;
        String savings;
    }

    private static class DealInfo {
        String storeName;
        String storeLogo;
        String price;
        String retailPrice;
        String savings;
    }
}