/* Copyright (C) 2014 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.example.games.catt2;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.RatingBar;
import android.widget.RatingBar.OnRatingBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.appstate.AppStateManager;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.GamesStatusCodes;
import com.google.android.gms.games.snapshot.Snapshot;
import com.google.android.gms.games.snapshot.SnapshotMetadata;
import com.google.android.gms.games.snapshot.SnapshotMetadataChange;
import com.google.android.gms.games.snapshot.Snapshots;
import com.google.android.gms.plus.Plus;
import com.google.example.games.basegameutils.BaseGameUtils;

import java.math.BigInteger;
import java.util.Calendar;
import java.util.Random;

/**
 * Collect All the Stars sample. This sample demonstrates how to use the cloud save features
 * of the Google Play game services API. It's a "game" where there are several worlds
 * with several levels each, and on each level the player can get from 0 to 5 stars.
 * The progress of the player is saved to the cloud and kept in sync across all their devices.
 * If they earn 5 stars on level 1 on one device and then earn 4 stars on level 2 on a different
 * device, upon synchronizing the consolidated progress will be 5 stars on level 1 AND
 * 4 stars on level 2. If they clear the same level on two different devices, then the biggest
 * star rating of the two will apply.
 *
 * It's worth noting that this sample also works offline, and even when the player is not
 * signed in. In all cases, the progress is saved locally as well.
 *
 * @author Bruno Oliveira (Google)
 */
public class MainActivity extends Activity
    implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
    View.OnClickListener, OnRatingBarChangeListener {


  private static final String TAG = "CollectAllTheStars2";

  // Request code used to invoke sign in user interactions.
  private static final int RC_SIGN_IN = 9001;

  // Request code for listing saved games
  private static final int RC_LIST_SAVED_GAMES = 9002;

  // Client used to interact with Google APIs.
  private GoogleApiClient mGoogleApiClient;

  // Are we currently resolving a connection failure?
  private boolean mResolvingConnectionFailure = false;

  // Has the user clicked the sign-in button?
  private boolean mSignInClicked = false;

  // Set to true to automatically start the sign in flow when the Activity starts.
  // Set to false to require the user to click the button in order to sign in.
  private boolean mAutoStartSignInFlow = true;

    // current save game
    SaveGame mSaveGame = new SaveGame();

    private String currentSaveName = "snapshotTemp";

    // world we're currently viewing
    int mWorld = 1;
    final int WORLD_MIN = 1, WORLD_MAX = 20;
    final int LEVELS_PER_WORLD = 12;

    // level we're currently "playing"
    int mLevel = 0;

    // state of "playing" - used to make the back button work correctly
    boolean mInLevel = false;

    // progress dialog we display while we're loading state from the cloud
    ProgressDialog mLoadingDialog = null;

    // whether we already loaded the state the first time (so we don't reload
    // every time the activity goes to the background and comes back to the foreground)
    boolean mAlreadyLoadedState = false;

    // the level buttons (the ones the user clicks to play a given level)
    final static int[] LEVEL_BUTTON_IDS = {
        R.id.button_level_1, R.id.button_level_2, R.id.button_level_3, R.id.button_level_4,
        R.id.button_level_5, R.id.button_level_6, R.id.button_level_7, R.id.button_level_8,
          R.id.button_level_9, R.id.button_level_10, R.id.button_level_11, R.id.button_level_12
     };

    // star strings (we use the Unicode BLACK STAR and WHITE STAR characters -- lazy graphics!)
    final static String[] STAR_STRINGS = {
        "\u2606\u2606\u2606\u2606\u2606", // 0 stars
        "\u2605\u2606\u2606\u2606\u2606", // 1 star
        "\u2605\u2605\u2606\u2606\u2606", // 2 stars
        "\u2605\u2605\u2605\u2606\u2606", // 3 stars
        "\u2605\u2605\u2605\u2605\u2606", // 4 stars
        "\u2605\u2605\u2605\u2605\u2605", // 5 stars
    };

    // Members related to the conflict resolution chooser of Snapshots.
    final static int MAX_SNAPSHOT_RESOLVE_RETRIES = 3;

    /**
     * You can capture the Snapshot selection intent in the onActivityResult method. The result
     * either indicates a new Snapshot was created (EXTRA_SNAPSHOT_NEW) or was selected.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == RC_SIGN_IN) {
          Log.d(TAG, "onActivityResult with requestCode == RC_SIGN_IN, responseCode="
              + resultCode + ", intent=" + intent);
          mSignInClicked = false;
          mResolvingConnectionFailure = false;
          if (resultCode == RESULT_OK) {
            mGoogleApiClient.connect();
          } else {
            BaseGameUtils.showActivityResultError(this,requestCode,resultCode,
                R.string.signin_failure, R.string.signin_other_error);
          }
        }
        else if (requestCode == RC_LIST_SAVED_GAMES) {
          if (intent != null) {
              if (intent.hasExtra(Snapshots.EXTRA_SNAPSHOT_METADATA)) {
                  // Load a snapshot.
                  SnapshotMetadata snapshotMetadata =
                          intent.getParcelableExtra(Snapshots.EXTRA_SNAPSHOT_METADATA);
                  currentSaveName = snapshotMetadata.getUniqueName();
                  loadFromSnapshot();
              } else if (intent.hasExtra(Snapshots.EXTRA_SNAPSHOT_NEW)) {
                  // Create a new snapshot named with a unique string
                  // TODO: check for existing snapshot, for now, add garbage text.
                  String unique = new BigInteger(281, new Random()).toString(13);
                  currentSaveName = "snapshotTemp-" + unique;
                  saveSnapshot();
              }
          }
        }
        super.onActivityResult(requestCode, resultCode, intent);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        log("onCreate.");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Create the Google Api Client with access to Plus and Games
        mGoogleApiClient = new GoogleApiClient.Builder(this)
            .addConnectionCallbacks(this)
            .addOnConnectionFailedListener(this)
            .addApi(Plus.API).addScope(Plus.SCOPE_PLUS_LOGIN)
            .addApi(Games.API).addScope(Games.SCOPE_GAMES)
            .addApi(AppStateManager.API).addScope(AppStateManager.SCOPE_APP_STATE)
            .addApi(Drive.API).addScope(Drive.SCOPE_APPFOLDER)
            .build();

        for (int id : LEVEL_BUTTON_IDS) {
            findViewById(id).setOnClickListener(this);
        }
        findViewById(R.id.button_next_world).setOnClickListener(this);
        findViewById(R.id.button_prev_world).setOnClickListener(this);
        findViewById(R.id.button_sign_in).setOnClickListener(this);
        findViewById(R.id.button_sign_out).setOnClickListener(this);
        ((RatingBar) findViewById(R.id.gameplay_rating)).setOnRatingBarChangeListener(this);
        mSaveGame = new SaveGame();
        updateUi();
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        // Player wants to force save or load.
        // NOTE: this button exists in this sample for debug purposes and so that you can
        // see the effects immediately. A game probably shouldn't have a "Load/Save"
        // button (or at least not one that's so prominently displayed in the UI).
        if (item.getItemId() == R.id.menu_sync) {
            loadFromSnapshot();
            return true;
        }
        if (item.getItemId() == R.id.menu_save) {
            saveSnapshot();
            return true;
        }
        if (item.getItemId() == R.id.menu_select)
        {
            showSnapshots(getString(R.string.title_saved_games), true, true);
        }
        return false;
    }


    @Override
    protected void onStart() {
        mLoadingDialog = new ProgressDialog(this);
        mLoadingDialog.setMessage(getString(R.string.loading_from_cloud));
        updateUi();
        super.onStart();
    }


    @Override
    protected void onStop() {
        if (mLoadingDialog != null) {
            mLoadingDialog.dismiss();
            mLoadingDialog = null;
        }
        super.onStop();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

    @Override
    public void onConnected(Bundle connectionHint) {

        // Sign-in worked!
        log("Sign-in successful! Loading game state from cloud.");
        showSignOutBar();
        if (!mAlreadyLoadedState) {
            showSnapshots(getString(R.string.title_load_game), false, false);
        }
    }


    @Override
    public void onConnectionSuspended(int i) {
      Log.d(TAG, "onConnectionSuspended() called. Trying to reconnect.");
      mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
      Log.d(TAG, "onConnectionFailed() called, result: " + connectionResult);

      if (mResolvingConnectionFailure) {
        Log.d(TAG, "onConnectionFailed() ignoring connection failure; already resolving.");
        return;
      }

      if (mSignInClicked || mAutoStartSignInFlow) {
        mAutoStartSignInFlow = false;
        mSignInClicked = false;
        mResolvingConnectionFailure = BaseGameUtils.resolveConnectionFailure(this, mGoogleApiClient,
            connectionResult, RC_SIGN_IN, getString(R.string.signin_other_error));
      }
      showSignInBar();
    }


    @Override
    public void onBackPressed() {
      if (mInLevel) {
        updateUi();
        findViewById(R.id.screen_gameplay).setVisibility(View.GONE);
        findViewById(R.id.screen_main).setVisibility(View.VISIBLE);
        mInLevel = false;
      }
      else {
        super.onBackPressed();
      }
    }

    /** Called when the "sign in" or "sign out" button is clicked. */
    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.button_sign_in:
                // Check to see the developer who's running this sample code read the instructions :-)
                // NOTE: this check is here only because this is a sample! Don't include this
                // check in your actual production app.
                if (!BaseGameUtils.verifySampleSetup(this, R.string.app_id)) {
                  Log.w(TAG, "*** Warning: setup problems detected. Sign in may not work!");
                }

                // start the sign-in flow
                Log.d(TAG, "Sign-in button clicked");
                mSignInClicked = true;
                mGoogleApiClient.connect();
                break;
            case R.id.button_sign_out:
                // sign out.
                mSignInClicked = false;
                Games.signOut(mGoogleApiClient);
                mGoogleApiClient.disconnect();
                showSignInBar();
                mSaveGame = new SaveGame();
                updateUi();
                break;
            case R.id.button_next_world:
                if (mGoogleApiClient == null || !mGoogleApiClient.isConnected()) {
                  BaseGameUtils.makeSimpleDialog(this,getString(R.string.please_sign_in)).show();
                  return;
                }
                if (mWorld < WORLD_MAX) {
                    mWorld++;
                    updateUi();
                }
                break;
            case R.id.button_prev_world:
                if (mGoogleApiClient == null || !mGoogleApiClient.isConnected()) {
                  BaseGameUtils.makeSimpleDialog(this,getString(R.string.please_sign_in)).show();
                  return;
                }
                if (mWorld > WORLD_MIN) {
                    mWorld--;
                    updateUi();
                }
                break;
            default:
                if (mGoogleApiClient == null || !mGoogleApiClient.isConnected()) {
                  BaseGameUtils.makeSimpleDialog(this,getString(R.string.please_sign_in)).show();
                  return;
                }
                for (int i = 0; i < LEVEL_BUTTON_IDS.length; ++i) {
                    if (view.getId() == LEVEL_BUTTON_IDS[i]) {
                        launchLevel(i + 1);
                        return;
                    }
                }
        }
    }

    /**
     * Gets a screenshot to use with snapshots. Note that in practice you probably do not want to
     * use this approach because tablet screen sizes can become pretty large and because the image
     * will contain any UI and layout surrounding the area of interest.
     */
    Bitmap getScreenShot() {
        View root = findViewById(R.id.screen_main);
        Bitmap coverImage;
        try {
            root.setDrawingCacheEnabled(true);
            Bitmap base = root.getDrawingCache();
            coverImage = base.copy(base.getConfig(), false /* isMutable */);
        } catch (Exception ex) {
            Log.i(TAG, "Failed to create screenshot", ex);
            coverImage = null;
        } finally {
            root.setDrawingCacheEnabled(false);
        }
        return coverImage;
    }


    /** Shows the user's snapshots. */
  void showSnapshots(String title, boolean allowAdd, boolean allowDelete) {
        int maxNumberOfSavedGamesToShow = 5;
        Intent snapshotIntent = Games.Snapshots.getSelectSnapshotIntent(
            mGoogleApiClient, title, allowAdd, allowDelete, maxNumberOfSavedGamesToShow);
        startActivityForResult(snapshotIntent, RC_LIST_SAVED_GAMES);
    }

    /**
     * Loads a Snapshot from the user's synchronized storage.
     */
    void loadFromSnapshot() {
        if (mLoadingDialog == null) {
            mLoadingDialog = new ProgressDialog(this);
            mLoadingDialog.setMessage(getString(R.string.loading_from_cloud));
        }
        mLoadingDialog.show();

        AsyncTask<Void, Void, Integer> task = new AsyncTask<Void, Void, Integer>() {
            @Override
            protected Integer doInBackground(Void... params) {
                Log.i(TAG, "Opening snapshot " + currentSaveName);
                Snapshots.OpenSnapshotResult result = Games.Snapshots.open(mGoogleApiClient,
                        currentSaveName, true).await();

                int status = result.getStatus().getStatusCode();

                Snapshot snapshot = null;
                if (status == GamesStatusCodes.STATUS_OK) {
                    snapshot = result.getSnapshot();
                } else if (status == GamesStatusCodes.STATUS_SNAPSHOT_CONFLICT) {

                    // if there is a conflict  - then resolve it.
                    snapshot = processSnapshotOpenResult(result, 3);

                    // if it resolved OK, change the status to Ok
                    if (snapshot != null) {
                      status = GamesStatusCodes.STATUS_OK;
                    }
                } else {
                    Log.e(TAG, "Error while loading: " + status);
                }

                if (snapshot != null) {
                  mSaveGame = new SaveGame(snapshot.readFully());
                  mAlreadyLoadedState = true;
                }

                return status;
            }

            @Override
            protected void onPostExecute(Integer status){
                Log.i(TAG, "Snapshot loaded: " + status);

                // Note that showing a toast is done here for debugging. Your application should
                // resolve the error appropriately to your app.
                if (status == GamesStatusCodes.STATUS_SNAPSHOT_NOT_FOUND){
                    Log.i(TAG,"Error: Snapshot not found");
                    Toast.makeText(getBaseContext(), "Error: Snapshot not found",
                            Toast.LENGTH_SHORT).show();
                } else if (status == GamesStatusCodes.STATUS_SNAPSHOT_CONTENTS_UNAVAILABLE) {
                    Log.i(TAG, "Error: Snapshot contents unavailable");
                    Toast.makeText(getBaseContext(), "Error: Snapshot contents unavailable",
                            Toast.LENGTH_SHORT).show();
                } else if (status == GamesStatusCodes.STATUS_SNAPSHOT_FOLDER_UNAVAILABLE){
                    Log.i(TAG, "Error: Snapshot folder unavailable");
                    Toast.makeText(getBaseContext(), "Error: Snapshot folder unavailable.",
                            Toast.LENGTH_SHORT).show();
                }

                if (mLoadingDialog != null) {
                    mLoadingDialog.dismiss();
                    mLoadingDialog = null;
                }
                hideAlertBar();
                updateUi();
      }
        };

        task.execute();
    }


    /**
     * Conflict resolution for when Snapshots are opened.
     * @param result The open snapshot result to resolve on open.
     * @return The opened Snapshot on success; otherwise, returns null.
     */
    Snapshot processSnapshotOpenResult(Snapshots.OpenSnapshotResult result, int retryCount){
        Snapshot mResolvedSnapshot;
        retryCount++;
        int status = result.getStatus().getStatusCode();

        Log.i(TAG, "Save Result status: " + status);

        if (status == GamesStatusCodes.STATUS_OK) {
            return result.getSnapshot();
        } else if (status == GamesStatusCodes.STATUS_SNAPSHOT_CONTENTS_UNAVAILABLE) {
            return result.getSnapshot();
        } else if (status == GamesStatusCodes.STATUS_SNAPSHOT_CONFLICT){
            Snapshot snapshot = result.getSnapshot();
            Snapshot conflictSnapshot = result.getConflictingSnapshot();

            // Resolve between conflicts by selecting the newest of the conflicting snapshots.
            mResolvedSnapshot = snapshot;

            if (snapshot.getMetadata().getLastModifiedTimestamp() <
                    conflictSnapshot.getMetadata().getLastModifiedTimestamp()){
                mResolvedSnapshot = conflictSnapshot;
            }

            Snapshots.OpenSnapshotResult resolveResult = Games.Snapshots.resolveConflict(
                    mGoogleApiClient, result.getConflictId(), mResolvedSnapshot)
                    .await();

            if (retryCount < MAX_SNAPSHOT_RESOLVE_RETRIES){
                return processSnapshotOpenResult(resolveResult, retryCount);
            } else {
                String message = "Could not resolve snapshot conflicts";
                Log.e(TAG, message);
                Toast.makeText(getBaseContext(), message, Toast.LENGTH_LONG).show();
            }

        }
        // Fail, return null.
        return null;
    }


    /**
     * Prepares saving Snapshot to the user's synchronized storage, conditionally resolves errors,
     * and stores the Snapshot.
     */
    void saveSnapshot() {
        AsyncTask<Void, Void, Snapshots.OpenSnapshotResult> task =
                new AsyncTask<Void, Void, Snapshots.OpenSnapshotResult>() {
                    @Override
                    protected Snapshots.OpenSnapshotResult doInBackground(Void... params) {
                        Snapshots.OpenSnapshotResult result = Games.Snapshots.open(mGoogleApiClient,
                                currentSaveName, true).await();
                        return result;
                    }

                    @Override
                    protected void onPostExecute(Snapshots.OpenSnapshotResult result) {
                        Snapshot toWrite = processSnapshotOpenResult(result, 0);

                        Log.i(TAG, writeSnapshot(toWrite));
                    }
                };

        task.execute();
    }

    /**
     * Generates metadata, takes a screenshot, and performs the write operation for saving a
     * snapshot.
     */
    private String writeSnapshot(Snapshot snapshot){
        // Set the data payload for the snapshot.
        snapshot.writeBytes(mSaveGame.toBytes());

        // Save the snapshot.
        SnapshotMetadataChange metadataChange = new SnapshotMetadataChange.Builder()
                .setCoverImage(getScreenShot())
                .setDescription("Modified data at: " + Calendar.getInstance().getTime())
                .build();
        Games.Snapshots.commitAndClose(mGoogleApiClient, snapshot, metadataChange);
        return snapshot.toString();
    }


    /** Shows the "sign in" bar (explanation and button). */
    private void showSignInBar() {
        findViewById(R.id.sign_in_bar).setVisibility(View.VISIBLE);
        findViewById(R.id.sign_out_bar).setVisibility(View.GONE);
    }

  /** Shows the "sign out" bar (explanation and button). */
  private void showSignOutBar() {
    findViewById(R.id.sign_in_bar).setVisibility(View.GONE);
    findViewById(R.id.sign_out_bar).setVisibility(View.VISIBLE);
  }


    /** Updates the game UI. */
    private void updateUi() {
        ((TextView) findViewById(R.id.world_display)).setText(getString(R.string.world)
                + " " + mWorld);
        for (int i = 0; i < LEVELS_PER_WORLD; i++) {
            int levelNo = i + 1; // levels are numbered from 1
            Button b = (Button) findViewById(LEVEL_BUTTON_IDS[i]);
            int stars = mSaveGame.getLevelStars(mWorld, levelNo);
            b.setTextColor(getResources().getColor(stars > 0 ? R.color.ClearedLevelColor :
                    R.color.UnclearedLevelColor));
            b.setText(String.valueOf(mWorld) + "-" + String.valueOf(levelNo) + "\n" +
                    STAR_STRINGS[stars]);
        }

        // disable world changing if we are at the end of the list.
        Button button;
        button = (Button) findViewById(R.id.button_next_world);
        button.setEnabled(mWorld < WORLD_MAX);

        button = (Button) findViewById(R.id.button_prev_world);
        button.setEnabled(mWorld > WORLD_MIN);
    }


    /**
     * Loads the specified level state.
     * @param {int} level to load.
     */
    private void launchLevel(int level) {
        mLevel = level;
        ((TextView) findViewById(R.id.gameplay_level_display)).setText(
                getString(R.string.level) + " " + mWorld + "-" + mLevel);
        ((RatingBar) findViewById(R.id.gameplay_rating)).setRating(
                mSaveGame.getLevelStars(mWorld, mLevel));
        findViewById(R.id.screen_gameplay).setVisibility(View.VISIBLE);
        findViewById(R.id.screen_main).setVisibility(View.GONE);
        mInLevel = true;
    }


    @Override
    public void onRatingChanged(RatingBar ratingBar, float rating, boolean fromUser) {
        mSaveGame.setLevelStars(mWorld, mLevel, (int)rating);
        updateUi();
        findViewById(R.id.screen_gameplay).setVisibility(View.GONE);
        findViewById(R.id.screen_main).setVisibility(View.VISIBLE);

        mInLevel = false;
        // save new data to cloud
        saveSnapshot();
    }

    /** Prints a log message (convenience method). */
    void log(String message) {
        Log.d(TAG, message);
    }


    /** Shows an alert message. */
    private void showAlertBar(int resId) {
        ((TextView) findViewById(R.id.alert_bar)).setText(getString(resId));
        findViewById(R.id.alert_bar).setVisibility(View.VISIBLE);
    }


    /** Dismisses the previously displayed alert message. */
    private void hideAlertBar() {
        findViewById(R.id.alert_bar).setVisibility(View.GONE);
  }
}
