/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ca.ilanguage.aublog.ui;

//DONE add a preferences activity, get rid of the old account logic and replace it with a proper database provider

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.Toast;
import ca.ilanguage.aublog.R;
import ca.ilanguage.aublog.db.AuBlogHistoryDatabase;
import ca.ilanguage.aublog.db.AuBlogHistoryDatabase.AuBlogHistory;
//import ca.ilanguage.aublog.util.DebugLog;
import ca.ilanguage.aublog.preferences.NonPublicConstants;
import ca.ilanguage.aublog.preferences.PreferenceConstants;
import ca.ilanguage.aublog.preferences.SetPreferencesActivity;
import ca.ilanguage.aublog.service.NotifyingController;
import ca.ilanguage.aublog.util.UIConstants;

import com.google.android.apps.analytics.GoogleAnalyticsTracker;
import com.google.gdata.data.Feed;

public class MainMenuActivity extends Activity {

	/*
	 * Tracker heirarchy
	 * 1x = main menu activity
	 * 11 = click on drafts
	 * 12 = click on new entry
	 * 13 = click on user guide
	 * 14 = click on settings
	 * 2x = view drafts tree activity
	 * 3x = edit blog entry activity
	 * 4x = settings activity
	 * 5x = about activity (ie user guide)
	 * 6x = publish activity
	 * 
	 * bugs/errors have a 0 in them
	 */
	GoogleAnalyticsTracker tracker;
	private String mAuBlogInstallId;
	
	private View mStartButton;
	private View mOptionsButton;
	private View mExtrasButton;
	private View mDraftsButton;
	private View mBackground;
	private View mTicker;
	private Animation mButtonFlickerAnimation;
	private Animation mFadeOutAnimation;
	private Animation mAlternateFadeOutAnimation;
	private Animation mFadeInAnimation;
	private boolean mJustCreated;
	private String mBloggerAccount;
	private String mBloggerPassword;
	private Runnable generateDraftsTree;
	private ProgressDialog m_ProgressDialog = null; 
	private AudioManager mAudioManager;
    
	
	private final String MSG_KEY = "value";
	public static Feed resultFeed = null;

	int viewStatus = 0;

	private final static int WHATS_NEW_DIALOG = 0;
	private final static int GENERATING_TREE_DIALOG = 1;
	
	protected static final String TAG = "MainMenuActivity";
	
	@Override
	  protected void onDestroy() {
		String release = Build.VERSION.RELEASE;
	    if(release.equals("2.2")){
//	    	 Toast.makeText(MainMenuActivity.this, "You have Android 2.2, disconnecting the bluetooth after it has been uesd for audio playback will restart your Android. " +
//	    	 		"\n\nThe only availible workaround is to killing this app's process id right after the app exits.\n " +
//	    	 		"The bluetooth bug was fixed in Android 2.2.1 and above.", Toast.LENGTH_LONG).show();
	    	 //ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
		     //activityManager.killBackgroundProcesses("ca.ilanguage.aublog");
	    }
		// Stop the tracker when it is no longer needed.
	    tracker.stop();
		/*
		 * When app is shut down, audio should return to normal.
		 */
		mAudioManager.setMode(AudioManager.MODE_NORMAL);
        mAudioManager.setSpeakerphoneOn(true);
	    super.onDestroy();
	    /*
TODO move the process kill into the back button so that the main menu can be rotated without disapearing.

		 * This is a terrible workaround for issue http://code.google.com/p/android/issues/detail?id=9503 of using bluetooth audio on Android 2.2 phones.
		 * Summary: it kills the app instead of finishing normally 
		 */
	    if(release.equals("2.2")){
	    	//this does not show a force close, but does sucessfully allow the user to disconnect the bluetooth after they close aublog. 
	    	//if they have android 2.2 and they disconnect the bluetooth without quitting aublog then the device will reboot.
	    	 android.os.Process.killProcess(android.os.Process.myPid());
	    }else{
	    	//do nothing, bluetooth issue is fixed in 2.2.1 and above
	    }
	   
	  }

	// Create an anonymous implementation of OnClickListener

	private View.OnClickListener sOptionButtonListener = new View.OnClickListener() {
		public void onClick(View v) {

			tracker.setCustomVar(1, "Navigation Type", "Button click", 14);
			tracker.trackPageView("/settingsScreen");
			tracker.trackEvent(
		            "Clicks",  // Category
		            "Button",  // Action
		            "clicked settings: "+mAuBlogInstallId, // Label
		            14);       // Value

			Intent i = new Intent(getBaseContext(),
					SetPreferencesActivity.class);

			v.startAnimation(mButtonFlickerAnimation);
			mFadeOutAnimation
					.setAnimationListener(new StartActivityAfterAnimation(i));
			mBackground.startAnimation(mFadeOutAnimation);
			mStartButton.startAnimation(mAlternateFadeOutAnimation);
			mExtrasButton.startAnimation(mAlternateFadeOutAnimation);
			mDraftsButton.startAnimation(mAlternateFadeOutAnimation);
			mTicker.startAnimation(mAlternateFadeOutAnimation);

		}
	};

	private View.OnClickListener sExtrasButtonListener = new View.OnClickListener() {
		public void onClick(View v) {

			tracker.setCustomVar(1, "Navigation Type", "Button click", 13);
			tracker.trackPageView("/userGuideScreen");
			tracker.trackEvent(
		            "Clicks",  // Category
		            "Button",  // Action
		            "clicked user guide: "+mAuBlogInstallId, // Label
		            13);       // Value
			
			Intent i = new Intent(getBaseContext(), AboutActivity.class);

			v.startAnimation(mButtonFlickerAnimation);
			mButtonFlickerAnimation
					.setAnimationListener(new StartActivityAfterAnimation(i));

		}
	};
	/*
	 * http://stackoverflow.com/questions/1979524/android-splashscreen
	 */
	public class GenerateTreeTask extends AsyncTask<Void, Void, Boolean>{

		
		@Override
		protected Boolean doInBackground(Void... params) {
			generateDraftTree();
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return true;
		}
		protected void onPreExecute(){
			showDialog(GENERATING_TREE_DIALOG);
			
		}
		protected void onPostExecute(Boolean result){
			/*
			 * Just before control is returned to the UI thread (?) launch an intent to open the 
			 * drafts tree activity
			 */
			Intent i = new Intent(getBaseContext(), ViewDraftTreeActivity.class);
			startActivity(i);
			dismissDialog(GENERATING_TREE_DIALOG);
		}
		
	}
	private View.OnClickListener sDraftsButtonListener = new View.OnClickListener() {
		public void onClick(View v) {
			tracker.setCustomVar(1, "Navigation Type", "Button click", 11);
			tracker.trackPageView("/viewDraftsTreeScreen");
			tracker.trackEvent(
		            "Clicks",  // Category
		            "Button",  // Action
		            "clicked drafts: "+mAuBlogInstallId, // Label
		            11);       // Value

			/*
			 * If the drafts tree is fresh (no new changes) return.
			 */
			SharedPreferences prefs = getSharedPreferences(PreferenceConstants.PREFERENCE_NAME, MODE_PRIVATE);
			if  (true == prefs.getBoolean(PreferenceConstants.PREFERENCE_DRAFT_TREE_IS_FRESH,false) ){
				Toast.makeText(MainMenuActivity.this,
						"Not re-creating drafts tree, using cached. ",
						Toast.LENGTH_LONG).show();
				tracker.trackEvent(
			            "CPU",  // Category
			            "Use",  // Action
			            "not creating new drafts tree: "+mAuBlogInstallId, // Label
			            111);   
				Intent i = new Intent(getBaseContext(), ViewDraftTreeActivity.class);

				v.startAnimation(mButtonFlickerAnimation);
				mButtonFlickerAnimation
						.setAnimationListener(new StartActivityAfterAnimation(i));
				return ;// "no tree created, it is already fresh";
			}

			/*
			 * Else if the drafts tree is not fresh, create a new Async task to generate the drafts tree
			 */
//			generateDraftsTree = new Runnable(){
//				@Override
//				public void run() {
//					generateDraftTree();
//				}
//			};
//			Thread thread =  new Thread(null, generateDraftsTree, "MagentoBackground");
//			thread.start();
			tracker.trackEvent(
		            "CPU",  // Category
		            "Use",  // Action
		            "creating new drafts tree: "+mAuBlogInstallId, // Label
		            112);  
			new GenerateTreeTask().execute();
			
			
			
			/*
			 * Mean while set the flag that the draft tree is fresh
			 */
			SharedPreferences.Editor editor = prefs.edit();
	    	editor.putBoolean(PreferenceConstants.PREFERENCE_DRAFT_TREE_IS_FRESH,true);
	    	editor.commit();
			
			

		}
	};

	private View.OnClickListener sStartButtonListener = new View.OnClickListener() {
		public void onClick(View v) {

			// Intent i = new Intent(getBaseContext(),
			// DifficultyMenuActivity.class);
			// i.putExtra("newGame", true);

			/*
			 * A full working sample client, containing all the sample code
			 * shown in this document, is available in the Java client library
			 * distribution, under the directory
			 * gdata/java/sample/blogger/BloggerClient.java. I. Public feeds
			 * don't require any authentication, but they are read-only. If you
			 * want to modify blogs, then your client needs to authenticate
			 * before requesting private feeds. this document assume you have an
			 * authenticated GoogleService object.
			 */

			// Alert
			// .showAlert(MainMenuActivity.this,
			// "Profile is not created",
			// "Please, input 'login/password' in settings");
			
			tracker.setCustomVar(1, "Navigation Type", "Button click", 12);
			tracker.trackPageView("/editBlogEntryScreen");
			tracker.trackEvent(
		            "Clicks",  // Category
		            "Button",  // Action
		            "clicked new entry: "+mAuBlogInstallId, // Label
		            12);       // Value

			Intent i = new Intent(getBaseContext(), EditBlogEntryActivity.class);

			Uri uri = getContentResolver().insert(AuBlogHistory.CONTENT_URI,
					null);
			// If we were unable to create a new blog entry, then just finish
			// this activity. A RESULT_CANCELED will be sent back to the
			// original activity if they requested a result.
			if (uri == null) {
				Log.e(TAG, "Failed to insert new blog entry into "
						+ getIntent().getData());
				Toast.makeText(
						MainMenuActivity.this,
						"Failed to insert new blog entry into the database. You can go to your devices settings, choose Aublog and click Clear data to re-create the database."
								+ getIntent().getData() + " with this uri"
								+ AuBlogHistory.CONTENT_URI, Toast.LENGTH_LONG)
						.show();
				tracker.trackEvent(
			            "Database",  // Category
			            "Bug",  // Action
			            "cannot create new entry: "+mAuBlogInstallId, // Label
			            10);       // Value

			} else {
				i.setData(uri);
				v.startAnimation(mButtonFlickerAnimation);
				mButtonFlickerAnimation
						.setAnimationListener(new StartActivityAfterAnimation(i));
			}
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		tracker = GoogleAnalyticsTracker.getInstance();

	    // Start the tracker in manual dispatch mode...
	    tracker.start(NonPublicConstants.NONPUBLIC_GOOGLE_ANALYTICS_UA_ACCOUNT_CODE, 20, this);

	    // ...alternatively, the tracker can be started with a dispatch interval (in seconds).
	    //tracker.start("UA-YOUR-ACCOUNT-HERE", 20, this);
	    SharedPreferences prefs = getSharedPreferences(PreferenceConstants.PREFERENCE_NAME, MODE_PRIVATE);
		mAuBlogInstallId = prefs.getString(PreferenceConstants.AUBLOG_INSTALL_ID, "0");
		
		mAudioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        
		setContentView(R.layout.mainmenu);

		mStartButton = findViewById(R.id.startButton);
		mOptionsButton = findViewById(R.id.optionButton);
		mBackground = findViewById(R.id.mainMenuBackground);

		if (mOptionsButton != null) {
			mOptionsButton.setOnClickListener(sOptionButtonListener);
		}

		mExtrasButton = findViewById(R.id.extrasButton);
		mExtrasButton.setOnClickListener(sExtrasButtonListener);

		mDraftsButton = findViewById(R.id.draftsButton);
		mDraftsButton.setOnClickListener(sDraftsButtonListener);

		mButtonFlickerAnimation = AnimationUtils.loadAnimation(this,
				R.anim.button_flicker);
		mFadeOutAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_out);
		mAlternateFadeOutAnimation = AnimationUtils.loadAnimation(this,
				R.anim.fade_out);
		mFadeInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in);

		mTicker = findViewById(R.id.ticker);
		if (mTicker != null) {
			mTicker.setFocusable(true);
			mTicker.requestFocus();
			mTicker.setSelected(true);
		}

		mJustCreated = true;

	}

	@Override
	protected void onPause() {
		super.onPause();

	}

	@Override
	protected void onResume() {
		super.onResume();

		mButtonFlickerAnimation.setAnimationListener(null);

		if (mStartButton != null) {

			
			// Change "start" to "continue" if its in a resume.
			SharedPreferences prefs = getSharedPreferences(PreferenceConstants.PREFERENCE_NAME, MODE_PRIVATE);
			

			((ImageView) mStartButton).setImageDrawable(getResources()
					.getDrawable(R.drawable.ui_button_start));
			mStartButton.setOnClickListener(sStartButtonListener);

			final int lastVersion = prefs.getInt(
					PreferenceConstants.PREFERENCE_LAST_VERSION, 0);

			if (Math.abs(lastVersion) < Math.abs(AuBlog.VERSION)) {
				// This is a new install or an upgrade.
				SharedPreferences.Editor editor = prefs.edit();
				
				/*
				 * If the install id isn't set, it's a new install so set the current timestamp appended with a 
				 *  a random number. 
				 *  
				 *  If the install id is set its an upgrade and don't need to set the install id
				 *  
				 *  Note:
				 *  If the user wipes the aublog data using the device settings, it counts as a new install. 
				 *  
				 *  A user may have many installIds. InstallIDs are attached to server calls to anonymously identify the transcription files 
				 *  so that users can connect them to their aublog accounts at a later date. 
				 */
				final String installID = prefs.getString(PreferenceConstants.AUBLOG_INSTALL_ID, "0");
				if (installID.length()< 5){
					Long currentTimeStamp = System.currentTimeMillis();
					Long randomNumberToAvoidSameSecondInstallsClash = (Math.round(Math.random()*10000));
					String newInstallID = currentTimeStamp.toString()+randomNumberToAvoidSameSecondInstallsClash.toString();
					editor.putString(PreferenceConstants.AUBLOG_INSTALL_ID, newInstallID);
				}
				
				/* This code checks for device compatibility
				 *
				 *
				// Check the safe mode option.
				// Useful reference:
				// http://en.wikipedia.org/wiki/List_of_Android_devices
				if (Build.PRODUCT.contains("morrison") || // Motorola Cliq/Dext
						Build.MODEL.contains("Pulse") || // Huawei Pulse
						Build.MODEL.contains("U8220") || // Huawei Pulse
						Build.MODEL.contains("U8230") || // Huawei U8230
						Build.MODEL.contains("MB300") || // Motorola Backflip
						Build.MODEL.contains("MB501") || // Motorola Quench /
															// Cliq XT
						Build.MODEL.contains("Behold+II")) { // Samsung Behold
																// II
					// These are all models that users have complained about.
					// They likely use
					// the same buggy QTC graphics driver. Turn on Safe Mode by
					// default
					// for these devices.
					SharedPreferences.Editor editor = prefs.edit();
					editor.putBoolean(PreferenceConstants.PREFERENCE_SAFE_MODE,
							true);
					editor.commit();
				}
				*/

				

				if (lastVersion > 0 && lastVersion < 14) {
					// if the user has updated the app at specific versions can do something here
					
				}

				// show what's new message
				editor.putInt(PreferenceConstants.PREFERENCE_LAST_VERSION,
						AuBlog.VERSION);
				editor.commit();

				showDialog(WHATS_NEW_DIALOG);

			}

		}

		if (mBackground != null) {
			mBackground.clearAnimation();
		}

		if (mTicker != null) {
			mTicker.clearAnimation();
			mTicker.setAnimation(mFadeInAnimation);
		}

		if (mJustCreated) {
			if (mDraftsButton != null) {
				mDraftsButton.startAnimation(AnimationUtils.loadAnimation(this,
						R.anim.button_slide));
			}
			if (mStartButton != null) {
				Animation anim = AnimationUtils.loadAnimation(this,
						R.anim.button_slide);
				anim.setStartOffset(500L);
				mStartButton.startAnimation(anim);
			}
			if (mExtrasButton != null) {
				Animation anim = AnimationUtils.loadAnimation(this,
						R.anim.button_slide);
				anim.setStartOffset(500L);
				mExtrasButton.startAnimation(anim);
			}

			if (mOptionsButton != null) {
				Animation anim = AnimationUtils.loadAnimation(this,
						R.anim.button_slide);
				anim.setStartOffset(1000L);
				mOptionsButton.startAnimation(anim);
			}
			mJustCreated = false;

		} else {
			mStartButton.clearAnimation();
			mOptionsButton.clearAnimation();
			mExtrasButton.clearAnimation();
			mDraftsButton.clearAnimation();
		}

	}

	@Override
	protected Dialog onCreateDialog(int id) {
		Dialog dialog;
		if (id == WHATS_NEW_DIALOG) {
			dialog = new AlertDialog.Builder(this)
					.setTitle(R.string.whats_new_dialog_title)
					.setPositiveButton(R.string.whats_new_dialog_ok, null)
					.setMessage(R.string.whats_new_dialog_message).create();
			
		} 
		else if (id == GENERATING_TREE_DIALOG) {
			dialog = new ProgressDialog.Builder(this)
            		.setCancelable(true)
					.setTitle("Please wait")
					.setMessage("Generating the drafts tree, this may take a moment.").create();
		} else {
			dialog = super.onCreateDialog(id);
		}
		return dialog;
	}


	public String generateDraftTree() {

		
		
		/*
		 * TODO: use the appl cache for the drafts tree
		 * http://developer.android.com/guide/topics/data/data-storage.html
		 */
		// BufferedWriter mOut;

		
		
		
		
		String mResultsFile = "draft_tree_data.js";
		// FileWriter fstream;
		/*
		 * If you're using API Level 8 or greater, use getExternalFilesDir() to
		 * open a File that represents the external storage directory where you
		 * should save your files. This method takes a type parameter that
		 * specifies the type of subdirectory you want, such as DIRECTORY_MUSIC
		 * and DIRECTORY_RINGTONES (pass null to receive the root of your
		 * application's file directory). This method will create the
		 * appropriate directory if necessary. String path =
		 * Environment.getExternalStorageDirectory().getAbsolutePath() +
		 * "/Android/data/ca.ilanguage.aublog/files/";
		 */
		// String path =
		// Environment.getExternalStorageDirectory().getAbsolutePath() +
		// "/Android/data/ca.ilanguage.aublog/files/";

		
//		File file = new File(getCacheDir(), mResultsFile);
		File file = new File(getExternalFilesDir(null), mResultsFile);
		
		
		try {
			// // Make sure the Pictures directory exists.
			// boolean exists = (new File(path)).exists();
			// if (!exists){ new File(path).mkdirs(); }
			// Open output stream
			FileOutputStream fOut = new FileOutputStream(file);
			
			new File(PreferenceConstants.OUTPUT_AUBLOG_DIRECTORY).mkdirs();
			File jsonOnlyFile =  new File(PreferenceConstants.OUTPUT_AUBLOG_DIRECTORY+PreferenceConstants.OUTPUT_FILE_NAME_FOR_DRAFT_EXPORT);
			FileOutputStream exportJSonOnly = new FileOutputStream(jsonOnlyFile);
			
			// fstream = new FileWriter(mResultsFile,true);
			// mOut = new BufferedWriter(fstream);
			String begining = "var draftTreeData=";
			fOut.write((begining).getBytes());
			
			String id = AuBlogHistoryDatabase.ROOT_ID_DEFAULT;
			String root = "{id: \"" + id + "\",\nname: \"" + "Root"
					+ "\",\nhidden: \"" + "0" 
					+ "\",\ndata: { content:\"empty"
					+ "\"},\nchildren: [";
			fOut.write((root).getBytes());
			
			String recursiveSubTree = getSubtree(id);
			fOut.write(recursiveSubTree.getBytes());
			exportJSonOnly.write(recursiveSubTree.getBytes());
			
			String endRoot = "]\n};";
			fOut.write((endRoot).getBytes());
			fOut.flush();
			fOut.close();
						
			exportJSonOnly.flush();
			exportJSonOnly.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			Toast.makeText(
					MainMenuActivity.this,
					"The SDCARD isn't writeable. Is the device being used as a disk drive on a comptuer?\n "
							+ e.toString(), Toast.LENGTH_LONG).show();

		}

		
		
//    	try {
//			Thread.sleep(1000);
//		} catch (InterruptedException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
    	//from retrieving experiments which was original from where?
//    	runOnUiThread(returnRes);
		return "drafts tree file created";
	}
//	private Runnable returnRes = new Runnable() {
//
//		@Override
//		public void run() {
//			
//			m_ProgressDialog.dismiss();
//			
//		}
//	};

	public String getSubtree(String id) {
		/*
		 * TODO get location of audio file so that can add a class to the space tree that indicates its playable, and a button to play it, requires adding to the projection?
		 */
		String[] PROJECTION = new String[] { AuBlogHistory._ID, // 0
				AuBlogHistory.ENTRY_TITLE, AuBlogHistory.ENTRY_CONTENT, // 2
				AuBlogHistory.ENTRY_LABELS, AuBlogHistory.PUBLISHED, // 4
				AuBlogHistory.DELETED, AuBlogHistory.PARENT_ENTRY // 6
		};
		String node = "";
		Boolean firstChild = true;
		try {

			/*
			 * find all nodes with this node as its parent
			 */
			Cursor cursor = managedQuery(AuBlogHistory.CONTENT_URI, PROJECTION,
					AuBlogHistory.PARENT_ENTRY + "=" + id, null, null);
			// Toast.makeText(MainMenuActivity.this,
			// "There are \n"+cursor.getCount()+" daughters",
			// Toast.LENGTH_LONG).show();
			if ((cursor != null)) {
				// Requery in case something changed while paused (such as the
				// title)
				cursor.requery();
				// Make sure we are at the one and only row in the cursor.
				cursor.moveToFirst();
				/*
				 * if this node is flagged as deleted, abort the subtree and the
				 * node
				 */
				String nodeAsString = "id:" + cursor.getString(0) + ":\ntitle:"
						+ cursor.getString(1) + ":\ncontent:"
						+ cursor.getString(2) + ":\nlabels:"
						+ cursor.getString(3) + ":\npublished:"
						+ cursor.getString(4) + ":\ndeleted:"
						+ cursor.getString(5) + ":\nparent:"
						+ cursor.getString(6) + ":";
				// Toast.makeText(MainMenuActivity.this,
				// "Full post info:"+nodeAsString, Toast.LENGTH_LONG).show();
				/*
				 * getting node as string here fails to retrieve all nodes, instead use json format with all info even though it bloats the json file. 
				 */
				//mEntireBlogDBasString=mEntireBlogDBasString+nodeAsString+"\n\n";

				if ("1".equals(cursor.getString(5))) {
					// Toast.makeText(MainMenuActivity.this,
					// "A deleted/hidden post:"+nodeAsString,
					// Toast.LENGTH_LONG).show();
					// cursor.moveToLast();
					// cursor.moveToNext();
				} else {
					// Toast.makeText(MainMenuActivity.this,
					// "Post:"+nodeAsString, Toast.LENGTH_LONG).show();

				}
				/*
				 * for each daughter, print the daughter and her subtree,
				 *  include the text in the data, even though it bloats the json file unneccisarily for the draft tree visualization. 
				 * ideally though, clicking on a node could pop up its contents. 
				 * how to access the array in the data element:answer, the data element is used for styling the node, so as long as you add a well formed array entry, its okay.
				 * http://stackoverflow.com/questions/5519097/javascript-infovis-spacetree-individual-node-styling
				 * 
				 * use TextUtils.htmlEncode to make it safe to put in the json, must decode it in the javascript if want to display the info later. 
				 * 
				 * Blog content
				 * 1. replacing carriage returns with <p> tags
				 * 2. running it through html encode to save it frmo breaking the json.
				 * 
				 * 
				 * Blog title
				 * 1. running it through the html encode to catch any french accents in the blog titles, not expecting any other html elements in a title however. 
				 */
				while (cursor.isAfterLast() == false) {
					if (!firstChild) {
						node = node + ",";
					}
					String Id = cursor.getString(0);
					node = node + "{\nid: \"" + Id + "\",\nname: \"";
					if ("1".equals(cursor.getString(5))) {
						node = node + "*";
					} // if the node is flagged as deleted write a star
					int height = (int) cursor.getString(1).length() * 2 + 10;
					if (height < 35){
						height = 35;
					}else if (height >300){
						height = 300;
					}
					node = node + TextUtils.htmlEncode(cursor.getString(1)) + "\",\nhidden: \""
							+ cursor.getString(5) + "\",\ndata: { content:\""
							+ TextUtils.htmlEncode( cursor.getString(2).replaceAll("(\r\n|\r|\n|\n\r)", "<p>") ) + "\"},\nchildren: [";

					/*
					 * find all nodes with this node as its parent
					 */
					node = node + getSubtree(Id);

					node = node + "]\n} ";
					firstChild = false;
					cursor.moveToNext();
				}
				// firstChild=true;
				// cursor.deactivate();
				String temp = "";

			}
		} catch (IllegalArgumentException e) {
			// Log.e(TAG, "IllegalArgumentException (DataBase failed)");
			Toast.makeText(
					MainMenuActivity.this,
					"Retrieval from DB failed with an illegal argument exception "
							+ e, Toast.LENGTH_LONG).show();
		} catch (Exception e) {
			// Log.e(TAG, "Exception (DataBase failed)");
			// Toast.makeText(MainMenuActivity.this,
			// "There was an error with the cursor "+e,
			// Toast.LENGTH_LONG).show();
		}

		// end root node
		// node = node+ "]\n} ";
		return node;
	}

	protected class StartActivityAfterAnimation implements
			Animation.AnimationListener {
		private Intent mIntent;

		StartActivityAfterAnimation(Intent intent) {
			mIntent = intent;
		}

		public void onAnimationEnd(Animation animation) {

			startActivity(mIntent);

			if (UIConstants.mOverridePendingTransition != null) {
				try {
					UIConstants.mOverridePendingTransition.invoke(
							MainMenuActivity.this, R.anim.activity_fade_in,
							R.anim.activity_fade_out);
				} catch (InvocationTargetException ite) {
//					DebugLog.d("Activity Transition",
//							"Invocation Target Exception");
				} catch (IllegalAccessException ie) {
//					DebugLog.d("Activity Transition",
//							"Illegal Access Exception");
				}
			}
		}

		public void onAnimationRepeat(Animation animation) {
			// TODO Auto-generated method stub

		}

		public void onAnimationStart(Animation animation) {
			// TODO Auto-generated method stub

		}

	}

}
