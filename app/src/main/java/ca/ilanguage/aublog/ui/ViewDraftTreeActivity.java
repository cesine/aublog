package ca.ilanguage.aublog.ui;

import java.io.File;
import java.io.IOException;

import com.google.android.apps.analytics.GoogleAnalyticsTracker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.webkit.ConsoleMessage;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import ca.ilanguage.aublog.R;
import ca.ilanguage.aublog.db.AuBlogHistoryDatabase;
import ca.ilanguage.aublog.db.AuBlogHistoryDatabase.AuBlogHistory;
import ca.ilanguage.aublog.preferences.NonPublicConstants;
import ca.ilanguage.aublog.preferences.PreferenceConstants;
import ca.ilanguage.aublog.preferences.SetPreferencesActivity;

/**
 * This activity wraps the view drafts tree html.
 *
 * @author cesine
 */
public class ViewDraftTreeActivity extends Activity {
    private Menu mMenu;
    GoogleAnalyticsTracker tracker;
    private String mAuBlogInstallId;
    private static final String TAG = "ViewDraftsActivity";

    private String mSelectedDraftId;
    //uri of the entry being edited.
    private Uri mUri;
    private Cursor mCursor;
    private MediaPlayer mMediaPlayer;

    int selectionStart;
    int selectionEnd;
    String mPostContent = "";
    String mPostTitle = "";
    String mPostLabels = "";
    private String[] PROJECTION = new String[]{
            AuBlogHistory._ID, //0
            AuBlogHistory.ENTRY_TITLE,
            AuBlogHistory.ENTRY_CONTENT, //2
            AuBlogHistory.ENTRY_LABELS,
            AuBlogHistory.PUBLISHED, //4
            AuBlogHistory.DELETED,
            AuBlogHistory.PARENT_ENTRY, //6
            AuBlogHistory.PUBLISHED_IN,
            AuBlogHistory.TIME_CREATED,//8
            AuBlogHistory.LAST_MODIFIED, ////this is a value generated by a database, use LAST_EDITED is a value generated the UI
            AuBlogHistory.AUDIO_FILE,//10
            AuBlogHistory.AUDIO_FILE_STATUS
    };


    private WebView mWebView;


    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.main_webview);

        tracker = GoogleAnalyticsTracker.getInstance();

        // Start the tracker in manual dispatch mode...
        tracker.start(NonPublicConstants.NONPUBLIC_GOOGLE_ANALYTICS_UA_ACCOUNT_CODE, 20, this);

        // ...alternatively, the tracker can be started with a dispatch interval (in seconds).
        //tracker.start("UA-YOUR-ACCOUNT-HERE", 20, this);

        //http://stackoverflow.com/questions/2465432/android-webview-completely-clear-the-cache
        /*
         * Errors in the javascript not loading the json. so trying to clear cache. 
         
        ViewDraftTreeActivity.this.deleteDatabase("webview.db");
        ViewDraftTreeActivity.this.deleteDatabase("webviewCache.db");
        */
        SharedPreferences prefs = getSharedPreferences(PreferenceConstants.PREFERENCE_NAME, MODE_PRIVATE);
        mAuBlogInstallId = prefs.getString(PreferenceConstants.AUBLOG_INSTALL_ID, "0");
        mSelectedDraftId = prefs.getString(PreferenceConstants.PREFERENCE_LAST_SELECTED_DRAFT_NODE, "4");

        mMediaPlayer = new MediaPlayer();


        mWebView = (WebView) findViewById(R.id.webview);
        mWebView.addJavascriptInterface(new JavaScriptInterface(this), "Android");

        WebSettings webSettings = mWebView.getSettings();
        webSettings.setSavePassword(false);
        webSettings.setSaveFormData(false);
        webSettings.setJavaScriptEnabled(true);
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setUserAgentString(
                webSettings.getUserAgentString()
                        + " "
                        + getString(R.string.user_agent_suffix)
        );
        
        
        /*
         * Add some debuging info
         */
        mWebView.setWebChromeClient(new WebChromeClient() {

            public boolean onJsAlert(WebView view, String url, String message, final android.webkit.JsResult result) {
                new AlertDialog.Builder(ViewDraftTreeActivity.this)

                        .setTitle("Draft Tree")
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok,
                                new AlertDialog.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        result.confirm();
                                    }
                                })
                        .setCancelable(false)
                        .create()
                        .show();

                return true;
            }

            ;
        });


        mWebView.loadUrl("file:///android_asset/view_draft_tree.html");


    }

    public class JavaScriptInterface {
        Context mContext;

        /*
         * TODO add some hooks in the javascript interface to the space tree to track user interaction with the tree, how often did they drag it, what is their prefered layout 
         * can set tehir prefered layout in the settings if that is a popular change in the draft tree. 
         */

        /**
         * Instantiate the interface and set the context
         */
        JavaScriptInterface(Context c) {
            mContext = c;

        }

        public void showToast(String toast) {
            Toast.makeText(mContext, toast, Toast.LENGTH_LONG).show();
        }

        public void setSelectedId(String id, String title) {
            mUri = AuBlogHistory.CONTENT_URI.buildUpon().appendPath(id).build();
            mSelectedDraftId = id;
            tracker.trackEvent(
                    mAuBlogInstallId,  // Category
                    "TreeNode",  // Action
                    "User clicked on " + id + "   " + title + " tree node in the view drafts tree : " + System.currentTimeMillis() + " : " + mAuBlogInstallId, // Label
                    (int) System.currentTimeMillis());       // Value

            //playNode();
        }

        public String getCenteredNode() {
            if (mSelectedDraftId == null) {
                return "1";
            } else {
                return mSelectedDraftId;
            }
        }

        public void playSelectedId() {
            //mUri = AuBlogHistory.CONTENT_URI.buildUpon().appendPath(id).build();
            playNode();
        }

        public void editId(String id) {
            tracker.trackPageView("/editBlogEntryScreen");
            tracker.trackEvent(
                    mAuBlogInstallId,  // Category
                    "Clicked Edit Entry",  // Action
                    "Clicked edit entry on view drafts page Toasted: Editing draft number " + id + " :  " + System.currentTimeMillis() + " : " + mAuBlogInstallId, // Label
                    (int) System.currentTimeMillis());       // Value

            Toast.makeText(mContext, "Editing draft number " + id, Toast.LENGTH_SHORT).show();
            Intent i = new Intent(getBaseContext(), EditBlogEntryActivity.class);
            i.setData(AuBlogHistory.CONTENT_URI.buildUpon().appendPath(id).build());
            startActivity(i);
            finish();
        }

        public void deleteId(String id) {
            tracker.trackEvent(
                    mAuBlogInstallId,  // Category
                    "Delete",  // Action
                    "Entry " + id + "was flagged as deleted in the view drafts tree : " + System.currentTimeMillis() + " : " + mAuBlogInstallId, // Label
                    (int) System.currentTimeMillis());       // Value
            Toast.makeText(mContext, "Flagged as deleted post number " + id, Toast.LENGTH_SHORT).show();
            Uri uri = AuBlogHistory.CONTENT_URI.buildUpon().appendPath(id).build();
            /*
			 * Flag entry as deleted
			 */
            ContentValues values = new ContentValues();
            values.put(AuBlogHistory.DELETED, "1");//sets deleted flag to true
            values.put(AuBlogHistory.PARENT_ENTRY, AuBlogHistoryDatabase.ROOT_TRASH_TREE);
            getContentResolver().update(uri, values, null, null);
//			getContentResolver().delete(uri, null, null);
            //Toast.makeText(ViewDraftTreeActivity.this, "Will refresh here Post " +uri.getLastPathSegment()+" deleted.", Toast.LENGTH_LONG).show();
			/*
	    	 * Flag the draft tree as needing to be regenerated
	    	 */
            SharedPreferences prefs = getSharedPreferences(PreferenceConstants.PREFERENCE_NAME, MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(PreferenceConstants.PREFERENCE_DRAFT_TREE_IS_FRESH, false);
            editor.commit();

            refreshTree();
        }

        public void refreshTree() {

            tracker.trackEvent(
                    mAuBlogInstallId,  // Category
                    "Potential javascript bug",  // Action
                    "User clicked refresh in the view drafts tree, they will probably only click this if the tree looks funny. : " + System.currentTimeMillis() + " : " + mAuBlogInstallId, // Label
                    (int) System.currentTimeMillis());       // Value
	    	/*
	    	 * TODO get the javascript to be regenerated, or simply open the json file and change the deleted flag on that entry?
	    	 */
            Intent i = new Intent(getBaseContext(), ViewDraftTreeActivity.class);
            startActivity(i);
            finish();
        }

        public void exportTree() {
//	    	Intent intent = new Intent();
//	    	intent.setAction(android.content.Intent.ACTION_VIEW);
//	    	intent.setDataAndType(Uri.fromFile(file), "text/*");
//	    	startActivity(intent); 

            tracker.trackEvent(
                    mAuBlogInstallId,  // Category
                    "Export data",  // Action
                    "User clicked on email/export drafts tree from the view drafts tree : " + System.currentTimeMillis() + " : " + mAuBlogInstallId, // Label
                    (int) System.currentTimeMillis());       // Value
            File file = new File(PreferenceConstants.OUTPUT_AUBLOG_DIRECTORY + PreferenceConstants.OUTPUT_FILE_NAME_FOR_DRAFT_EXPORT);

            Intent mailto = new Intent(Intent.ACTION_SEND);
            mailto.setType("message/rfc822"); // use from live device
            mailto.putExtra(Intent.EXTRA_EMAIL, new String[]{""});
            mailto.putExtra(Intent.EXTRA_SUBJECT, "Backup of AuBlog Drafts");
            mailto.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
            mailto.putExtra(Intent.EXTRA_TEXT, getString(R.string.email_exported_json_text_content));
            startActivity(Intent.createChooser(mailto, "Select email application."));
        }


    }//end javascript interface.

    private void playNode() {
        if (mUri != null) {
            mCursor = managedQuery(mUri, PROJECTION, null, null, null);
            if (mCursor != null) {
                // Requery in case something changed while paused (such as the title)
                mCursor.requery();
                // Make sure we are at the one and only row in the cursor.
                mCursor.moveToFirst();
                try {
                    String audioResultsFile = mCursor.getString(10);
                    if (audioResultsFile.length() > 5) {
                        if (mMediaPlayer != null) {
                            if (mMediaPlayer.isPlaying() == true) {
                                mMediaPlayer.stop();
                            }
                            mMediaPlayer.release();
                            mMediaPlayer = null;
                        }
                        try {
                            mMediaPlayer = new MediaPlayer();
                            //mMediaPlayer.setLooping(true);
                            mMediaPlayer.setDataSource(audioResultsFile);
                            mMediaPlayer.prepare();
                            mMediaPlayer.start();
                            tracker.trackEvent(
                                    mAuBlogInstallId,  // Category
                                    "TreeNode",  // Action
                                    "User long clicked on a node and it played node " + mSelectedDraftId + " audio file " + audioResultsFile + " tree node to play the audio in the view drafts tree : " + System.currentTimeMillis() + " : " + mAuBlogInstallId, // Label
                                    (int) System.currentTimeMillis());       // Value

                        } catch (IllegalArgumentException e) {
                            Toast.makeText(ViewDraftTreeActivity.this, "Problem with opening the audio file " + e, Toast.LENGTH_LONG).show();
                        } catch (IllegalStateException e) {
                            Toast.makeText(ViewDraftTreeActivity.this, "Problem with opening the audio file " + e, Toast.LENGTH_LONG).show();
                        } catch (IOException e) {
                            Toast.makeText(ViewDraftTreeActivity.this, "Problem with opening the audio file " + e, Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Toast.makeText(ViewDraftTreeActivity.this, "No attached audio file ", Toast.LENGTH_LONG).show();
                    }
                } catch (IllegalArgumentException e) {
                    // Log.e(TAG, "IllegalArgumentException (DataBase failed)");
                    tracker.trackEvent(
                            "Database",  // Category
                            "Bug",  // Action
                            "Retrieval from DB failed with an illegal argument exception " + e + " : " + mAuBlogInstallId, // Label
                            201);       // Value
                    Toast.makeText(ViewDraftTreeActivity.this, "Retrieval from DB failed with an illegal argument exception " + e, Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    // Log.e(TAG, "Exception (DataBase failed)");
                    tracker.trackEvent(
                            "Database",  // Category
                            "Bug",  // Action
                            "The cursor returned is " + e + " : " + mAuBlogInstallId, // Label
                            202);       // Value
                    //Toast.makeText(EditBlogEntryActivity.this, "The cursor returned is "+e, Toast.LENGTH_LONG).show();
                }
            }//end if for cursor not null
        }//end if for muri not null
    }//end playnode

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Save UI state changes to the savedInstanceState.
        // This bundle will be passed to onCreate if the process is
        // killed and restarted.
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        // Restore UI state from the savedInstanceState.
        // This bundle has also been passed to onCreate.

    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {

        if (mMediaPlayer != null) {
            mMediaPlayer.release();
            mMediaPlayer = null;
        }

        SharedPreferences prefs = getSharedPreferences(PreferenceConstants.PREFERENCE_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PreferenceConstants.PREFERENCE_LAST_SELECTED_DRAFT_NODE, mSelectedDraftId);
        editor.commit();

        super.onDestroy();
        tracker.stop();
    }

    /**
     * Provides a hook for calling "alert" from javascript. Useful for
     * debugging your javascript.
     */
    final class MyWebChromeClient extends WebChromeClient {
        @Override
        public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
            Log.d(TAG, message);
            result.confirm();
            return true;
        }

        @Override
        public boolean onConsoleMessage(ConsoleMessage cm) {
            Log.d(TAG, cm.message() + " -- From line " + cm.lineNumber() + " of "
                    + cm.sourceId());
            return true;
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Hold on to this
        mMenu = menu;

        // Inflate the currently selected menu XML resource.
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.drafts_tree_menu, menu);


        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // For "Title only": Examples of matching an ID with one assigned in
            //                   the XML
            case R.id.open_settings:
                tracker.trackPageView("/settingsScreen");
                tracker.trackEvent(
                        mAuBlogInstallId,  // Category
                        "Clicked settings",  // Action
                        "Clicked settings on view drafts tree page " + System.currentTimeMillis() + " : " + mAuBlogInstallId, // Label
                        (int) System.currentTimeMillis());       // Value

                Intent i = new Intent(getBaseContext(), SetPreferencesActivity.class);
                startActivity(i);
                return true;
            case R.id.issue_tracker:
                tracker.trackPageView("/issueTracker");
                tracker.trackEvent(
                        mAuBlogInstallId,  // Category
                        "Clicked bugs",  // Action
                        "Clicked bugs on view drafts tree page " + System.currentTimeMillis() + " : " + mAuBlogInstallId, // Label
                        (int) System.currentTimeMillis());       // Value

                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://code.google.com/p/aublog/issues/entry"));
                startActivity(browserIntent);
                return true;
            case R.id.new_entry:
                tracker.trackPageView("/editBlogEntryScreen");
                tracker.trackEvent(
                        mAuBlogInstallId,  // Category
                        "Clicked New Entry",  // Action
                        "Clicked new entry on view drafts tree page " + System.currentTimeMillis() + " : " + mAuBlogInstallId, // Label
                        (int) System.currentTimeMillis());       // Value

                Intent intent = new Intent(getBaseContext(), EditBlogEntryActivity.class);

                Uri uri = getContentResolver().insert(AuBlogHistory.CONTENT_URI,
                        null);
                // If we were unable to create a new blog entry, then just finish
                // this activity. A RESULT_CANCELED will be sent back to the
                // original activity if they requested a result.
                if (uri == null) {
                    Log.e(TAG, "Failed to insert new blog entry into "
                            + getIntent().getData());
                    Toast.makeText(
                            ViewDraftTreeActivity.this,
                            "Failed to insert new blog entry into the database. You can go to your devices settings, choose Aublog and click Clear data to re-create the database."
                                    + getIntent().getData() + " with this uri"
                                    + AuBlogHistory.CONTENT_URI, Toast.LENGTH_LONG)
                            .show();
                    tracker.trackEvent(
                            "Database",  // Category
                            "Bug",  // Action
                            "cannot create new entry in the view drafts tree menu: " + mAuBlogInstallId, // Label
                            20);       // Value

                } else {
                    intent.setData(uri);
                    startActivity(intent);
                    finish();
                }
                return true;
            default:
                // Do nothing

                break;
        }

        return false;
    }
}
