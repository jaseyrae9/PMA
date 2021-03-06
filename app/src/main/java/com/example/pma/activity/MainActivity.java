package com.example.pma.activity;

import android.Manifest;
import android.app.ProgressDialog;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;

import com.example.pma.R;
import com.example.pma.adapter.SimpleRouteRecyclerViewAdapter;
import com.example.pma.database.DBContentProvider;
import com.example.pma.database.RouteSQLiteHelper;
import com.example.pma.model.BusStop;
import com.example.pma.model.DatabaseVersion;
import com.example.pma.model.Route;
import com.example.pma.model.Timetable;
import com.example.pma.network.RetrofitClientInstance;
import com.example.pma.service.GetDataService;
import com.example.pma.service.NotificationService;
import com.google.android.material.navigation.NavigationView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = "MainActivity";
    private static final String AUTHORITY = "com.example.pma";
    private static final String ROUTE_PATH = "route";
    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private boolean mTwoPane;

    /**
     * Reference to drawer.
     */
    private DrawerLayout drawer;

    /**
     * Reference to navigation view.
     */
    private NavigationView navigationView;

    private ProgressDialog progressDoalog;

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        Log.e(TAG, "\t\t onCreate()");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        if (findViewById(R.id.route_detail_container) != null) {
            // The detail container view will be present only in the
            // large-screen layouts (res/values-w900dp).
            // If this view is present, then the
            // activity should be in two-pane mode.
            mTwoPane = true;
        }

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (preferences.getBoolean("alarm",false)){
            scheduleJob();
        } else {
            JobScheduler scheduler = (JobScheduler) getSystemService(JOB_SCHEDULER_SERVICE);
            scheduler.cancel(1);
        }

        this.getPermissions();
    }

    @Override
    protected void onStart() {
        super.onStart();
        loadRoutes();
    }

    private void loadRoutes() {
        progressDoalog = new ProgressDialog(MainActivity.this);
        progressDoalog.setMessage("Učitavanje....");
        progressDoalog.show();

        SharedPreferences dbVersionPref = getSharedPreferences("Database", 0);

        /*Create handle for the RetrofitInstance interface*/
        final GetDataService service = RetrofitClientInstance.getRetrofitInstance().create(GetDataService.class);
        RouteSQLiteHelper dbHelper = new RouteSQLiteHelper(MainActivity.this, dbVersionPref.getInt("Version", 1));
        final SQLiteDatabase db = dbHelper.getWritableDatabase();

        Call<DatabaseVersion> call = service.getVersion();
        call.enqueue(new Callback<DatabaseVersion>() {
            @Override
            public void onResponse(Call<DatabaseVersion> call, Response<DatabaseVersion> response) {

                final Integer newVersion = response.body().getVersion();
                if(db.getVersion() < newVersion) {
                    Call<List<Route>> call2 = service.getAllRoutes();
                    call2.enqueue(new Callback<List<Route>>() {
                        @Override
                        public void onResponse(Call<List<Route>> call, Response<List<Route>> response) {
                            progressDoalog.dismiss();
                            generateDataList(response.body(), newVersion);
                        }

                        @Override
                        public void onFailure(Call<List<Route>> call, Throwable t) {
                            progressDoalog.dismiss();
                            Log.e("Greska", "1");
                            Toast.makeText(MainActivity.this, "Došlo je do greške...Molimo Vas da probate opet.", Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    progressDoalog.dismiss();

                    List<Route> routeList = new ArrayList<>();
                    Uri uri = DBContentProvider.CONTENT_URI_ROUTE;

                    String[] allColumns = {RouteSQLiteHelper.COLUMN_ID, RouteSQLiteHelper.COLUMN_NAME, RouteSQLiteHelper.COLUMN_DESCRIPTION, RouteSQLiteHelper.COLUMN_CITY};

                    Cursor cursor = getContentResolver().query(uri, allColumns, null, null, null);

                    cursor.moveToFirst();
                    while(!cursor.isAfterLast()) {
                        Route route = new Route();
                        route.setId(cursor.getInt(0));
                        route.setName(cursor.getString(1));
                        route.setDescription(cursor.getString(2));
                        route.setCity(cursor.getString(3));
                        routeList.add(route);

                        cursor.moveToNext();
                    }
                    cursor.close();

                    View recyclerView = findViewById(R.id.item_list);
                    assert recyclerView != null;
                    setupRecyclerView((RecyclerView) recyclerView, routeList);
                }
            }

            @Override
            public void onFailure(Call<DatabaseVersion> call, Throwable t) {
                progressDoalog.dismiss();
                Log.d("Greska", t.getMessage());
                Log.e("Greska", "2");
                Toast.makeText(MainActivity.this, "Došlo je do greške...Molimo Vas da probate opet.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void getPermissions() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED) {
                        return;
        } else {
            ActivityCompat.requestPermissions(this, new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    /*Method to generate List of data using RecyclerView with custom adapter*/
    private void generateDataList(List<Route> routeList, Integer newVersion) {
        // Save data to the database
        SharedPreferences dbVersionPref = getSharedPreferences("Database", 0);

        RouteSQLiteHelper dbHelper = new RouteSQLiteHelper(MainActivity.this, dbVersionPref.getInt("Version", 1));
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        dbHelper.onUpgrade(db,db.getVersion(),newVersion);
        {
            for(Route r : routeList) {
                ContentValues entry = new ContentValues();
                entry.put(RouteSQLiteHelper.COLUMN_ID, r.getId());
                entry.put(RouteSQLiteHelper.COLUMN_NAME, r.getName());
                entry.put(RouteSQLiteHelper.COLUMN_DESCRIPTION, r.getDescription());
                entry.put(RouteSQLiteHelper.COLUMN_CITY, r.getCity());
                MainActivity.this.getContentResolver().insert(DBContentProvider.CONTENT_URI_ROUTE, entry);

                for(BusStop bs : r.getBusStops()) {
                    ContentValues busStopEntry = new ContentValues();
                    busStopEntry.put(RouteSQLiteHelper.COLUMN_ID, bs.getId());
                    busStopEntry.put(RouteSQLiteHelper.COLUMN_NAME, bs.getName());
                    busStopEntry.put(RouteSQLiteHelper.COLUMN_LAT, bs.getLat());
                    busStopEntry.put(RouteSQLiteHelper.COLUMN_LNG, bs.getLng());
                    busStopEntry.put(RouteSQLiteHelper.COLUMN_ROUTE_ID, r.getId());
                    Uri uri = Uri.parse("content://" + AUTHORITY + "/" + ROUTE_PATH + "/" + r.getId().toString() + "/stop");
                    MainActivity.this.getContentResolver().insert(uri, busStopEntry);
                }
                for(Timetable tt: r.getTimetables()) {
                    ContentValues timetableEntry = new ContentValues();
                    timetableEntry.put(RouteSQLiteHelper.COLUMN_ID, tt.getId());
                    timetableEntry.put(RouteSQLiteHelper.COLUMN_TYPE, tt.getType());
                    timetableEntry.put(RouteSQLiteHelper.COLUMN_CONTENT, tt.getContent());
                    timetableEntry.put(RouteSQLiteHelper.COLUMN_ROUTE_ID, r.getId());

                    Uri uri = Uri.parse("content://" + AUTHORITY + "/" + ROUTE_PATH + "/" + r.getId().toString() + "/timetable");
                    MainActivity.this.getContentResolver().insert(uri, timetableEntry);
                }
            }
        }
        db.setVersion(newVersion);
        db.close();

        SharedPreferences.Editor editor = dbVersionPref.edit();
        editor.putInt("Version", newVersion);
        editor.commit();

        View recyclerView = findViewById(R.id.item_list);
        assert recyclerView != null;
        setupRecyclerView((RecyclerView) recyclerView, routeList);
    }

    private void setupRecyclerView(@NonNull RecyclerView recyclerView, List<Route> routeList) {
        List<Route> routeListCity = new ArrayList<>();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        for(Route r : routeList) {
            if(r.getCity().toLowerCase().equals(prefs.getString("city", "Novi Sad").toLowerCase())) {
                routeListCity.add(r);
            }
        }
        recyclerView.setAdapter(new SimpleRouteRecyclerViewAdapter(this, routeListCity, mTwoPane));
    }

    @Override
    public void onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        //Toast.makeText(this, "on Options Item Selected", Toast.LENGTH_SHORT).show();
        int id = item.getItemId();

        if(id == android.R.id.home){ // use android.R.id
            drawer.openDrawer(Gravity.LEFT);
        }

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case R.id.all_routs:
                Intent intent= new Intent(MainActivity.this,MainActivity.class);
                startActivity(intent);
                break;
            case R.id.settings:
                Intent intentSettings = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(intentSettings);
                break;
            case R.id.warnings:
                Intent intentNews = new Intent (MainActivity.this, NewsActivity.class);
                startActivity(intentNews);
                break;
            case R.id.favourite_routes:
                Intent intentFavouriteRoutes = new Intent(MainActivity.this, FavouriteRoutesActivity.class);
                startActivity(intentFavouriteRoutes);
                break;
        }
        return false;
    }

    public void scheduleJob() {
        ComponentName componentName = new ComponentName (getApplicationContext(), NotificationService.class);
        JobInfo jobInfo = new JobInfo.Builder(1, componentName)
                .setPeriodic(1000*60*15)
                .build();

        JobScheduler jobScheduler = (JobScheduler) getSystemService(JOB_SCHEDULER_SERVICE);
        jobScheduler.schedule(jobInfo);
    }
}
