/**
 * Copyright Google Inc. All Rights Reserved.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.codelab.friendlychat;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.appinvite.AppInvite;
import com.google.android.gms.appinvite.AppInviteInvitation;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.appindexing.Action;
import com.google.firebase.appindexing.FirebaseAppIndex;
import com.google.firebase.appindexing.FirebaseUserActions;
import com.google.firebase.appindexing.Indexable;
import com.google.firebase.appindexing.builders.Indexables;
import com.google.firebase.appindexing.builders.PersonBuilder;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import de.hdodenhof.circleimageview.CircleImageView;

public class MainActivity extends AppCompatActivity
        implements GoogleApiClient.OnConnectionFailedListener {

    public static class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageTextView;
        ImageView messageImageView;
        TextView messengerTextView;
        CircleImageView messengerImageView;

        public MessageViewHolder(View v) {
            super(v);
            messageTextView = (TextView) itemView.findViewById(R.id.messageTextView);
            messageImageView = (ImageView) itemView.findViewById(R.id.messageImageView);
            messengerTextView = (TextView) itemView.findViewById(R.id.messengerTextView);
            messengerImageView = (CircleImageView) itemView.findViewById(R.id.messengerImageView);
        }
    }

    private static final String TAG = "MainActivity";
    public static final String MESSAGES_CHILD = "messages";
    private static final int REQUEST_INVITE = 1;
    private static final int REQUEST_IMAGE = 2;
    private static final String LOADING_IMAGE_URL = "https://www.google.com/images/spin-32.gif";
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 10;
    public static final String ANONYMOUS = "anonymous";
    private static final String MESSAGE_SENT_EVENT = "message_sent";
    private String mUsername;
    private String mPhotoUrl;
    private SharedPreferences mSharedPreferences;
    private GoogleApiClient mGoogleApiClient;
    private static final String MESSAGE_URL = "http://friendlychat.firebase.google.com/message/";

    private Button mSendButton;
    private RecyclerView mMessageRecyclerView;
    private LinearLayoutManager mLinearLayoutManager;
    private ProgressBar mProgressBar;
    private EditText mMessageEditText;
    private ImageView mAddMessageImageView;

    // Firebase instance variables
    private FirebaseAuth firebaseAuth;
    private FirebaseUser firebaseUser;
    private FirebaseRemoteConfig firebaseRemoteConfig;
    private FirebaseAnalytics firebaseAnalytics;

    private DatabaseReference databaseReference;
    private FirebaseRecyclerAdapter<FriendlyMessage, MessageViewHolder> firebaseRecyclerAdapter;

    private AdView adView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        // Start ad
        adView = (AdView) findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().build();
        adView.loadAd(adRequest);

        // Set default username is anonymous.
        mUsername = ANONYMOUS;

        firebaseAnalytics = FirebaseAnalytics.getInstance(this);

        firebaseAuth = FirebaseAuth.getInstance();
        firebaseUser = firebaseAuth.getCurrentUser();
        if (firebaseUser == null){
            startActivity(new Intent(MainActivity.this, SignInActivity.class));
            finish();
            return;
        }else{
            mUsername = firebaseUser.getDisplayName();
            if(firebaseUser.getPhotoUrl() != null){
                mPhotoUrl = firebaseUser.getPhotoUrl().toString();
            }
        }

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this /* FragmentActivity */, this /* OnConnectionFailedListener */)
                .addApi(Auth.GOOGLE_SIGN_IN_API)
                .addApi(AppInvite.API)
                .build();

        // Initialize ProgressBar and RecyclerView.
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mMessageRecyclerView = (RecyclerView) findViewById(R.id.messageRecyclerView);
        mLinearLayoutManager = new LinearLayoutManager(this);
        mLinearLayoutManager.setStackFromEnd(true);
        mMessageRecyclerView.setLayoutManager(mLinearLayoutManager);

        firebaseRemoteConfig = FirebaseRemoteConfig.getInstance();

        FirebaseRemoteConfigSettings setting = new FirebaseRemoteConfigSettings.Builder()
                .setDeveloperModeEnabled(true)
                .build();
        Map<String, Object> defaultConfigMap = new HashMap<>();
        defaultConfigMap.put("friendly_msg_length", 10L);

        firebaseRemoteConfig.setConfigSettings(setting);
        firebaseRemoteConfig.setDefaults(defaultConfigMap);

        fetchConfig();

        databaseReference = FirebaseDatabase.getInstance().getReference();
        firebaseRecyclerAdapter = new FirebaseRecyclerAdapter<FriendlyMessage, MessageViewHolder>(
                FriendlyMessage.class,
                R.layout.item_message,
                MessageViewHolder.class,
                databaseReference.child(MESSAGES_CHILD)
        ) {
            @Override
            protected void populateViewHolder(final MessageViewHolder messageViewHolder, FriendlyMessage friendlyMessage, int i) {
                mProgressBar.setVisibility(View.INVISIBLE);
                if(friendlyMessage.getText() != null){
                    messageViewHolder.messageTextView.setText(friendlyMessage.getText());
                    messageViewHolder.messageTextView.setVisibility(View.VISIBLE);
                    messageViewHolder.messageImageView.setVisibility(View.GONE);

                    // write this message to the on-device index
                    FirebaseAppIndex.getInstance().update(getMessageIndexable(friendlyMessage));

                    // log a view action on it
                    FirebaseUserActions.getInstance().end(getMessageViewAction(friendlyMessage));
                }else{
                    String imageUrl = friendlyMessage.getImageUrl();
                    if(imageUrl.startsWith("gs://")){
                        StorageReference storageReference = FirebaseStorage.getInstance()
                                .getReferenceFromUrl(imageUrl);
                        storageReference.getDownloadUrl().addOnCompleteListener(new OnCompleteListener<Uri>() {
                            @Override
                            public void onComplete(@NonNull Task<Uri> task) {
                                if(task.isSuccessful()){
                                    String downloadUrl = task.getResult().toString();
                                    Glide.with(messageViewHolder.messageImageView.getContext())
                                            .load(downloadUrl)
                                            .into(messageViewHolder.messageImageView);
                                }else{
                                    Log.w(TAG, "getting url was unsuccessful" + task.getException());
                                }
                            }
                        });
                    }else{
                        Glide.with(messageViewHolder.messageImageView.getContext())
                                .load(friendlyMessage.getImageUrl())
                                .into(messageViewHolder.messageImageView);
                    }
                    messageViewHolder.messageImageView.setVisibility(View.VISIBLE);
                    messageViewHolder.messageTextView.setVisibility(View.GONE);
                }

                messageViewHolder.messengerTextView.setText(friendlyMessage.getName());
                if(friendlyMessage.getPhotoUrl() == null){
                    messageViewHolder.messengerImageView.setImageDrawable(ContextCompat.getDrawable(MainActivity.this,
                            R.drawable.ic_account_circle_black_36dp));
                }else{
                    Glide.with(MainActivity.this)
                            .load(friendlyMessage.getPhotoUrl())
                            .into(messageViewHolder.messengerImageView);
                }
            }

            @Override
            protected FriendlyMessage parseSnapshot(DataSnapshot snapshot) {
                FriendlyMessage friendlyMessage = super.parseSnapshot(snapshot);
                if(friendlyMessage != null){
                    friendlyMessage.setId(snapshot.getKey());
                }
                return friendlyMessage;
            }
        };

        firebaseRecyclerAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                super.onItemRangeInserted(positionStart, itemCount);
                int friendlyMessageCount = firebaseRecyclerAdapter.getItemCount();
                int lastVisiblePosition = mLinearLayoutManager.findLastVisibleItemPosition();

                if(lastVisiblePosition == -1 ||
                        (positionStart >= (friendlyMessageCount - 1) &&
                            lastVisiblePosition == (positionStart - 1))){
                    mMessageRecyclerView.scrollToPosition(positionStart);
                }
            }
        });

        mMessageRecyclerView.setAdapter(firebaseRecyclerAdapter);

        mMessageEditText = (EditText) findViewById(R.id.messageEditText);
        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(mSharedPreferences
                .getInt(CodelabPreferences.FRIENDLY_MSG_LENGTH, DEFAULT_MSG_LENGTH_LIMIT))});
        mMessageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (charSequence.toString().trim().length() > 0) {
                    mSendButton.setEnabled(true);
                } else {
                    mSendButton.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });

        mSendButton = (Button) findViewById(R.id.sendButton);
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Send messages on click.
                FriendlyMessage friendlyMessage = new FriendlyMessage(mMessageEditText.getText().toString(),
                        mUsername,
                        mPhotoUrl,
                        null /* no image */);
                databaseReference.child(MESSAGES_CHILD)
                        .push().setValue(friendlyMessage);
                mMessageEditText.setText("");
            }
        });

        mAddMessageImageView = (ImageView) findViewById(R.id.addMessageImageView);
        mAddMessageImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Select image for image message on click.
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
                startActivityForResult(intent, REQUEST_IMAGE);
            }
        });
    }

    public void fetchConfig(){
        long cacheExpiration = 3600; // 1 hour is 3600 seconds

        // Development mode should make each fetch go to server
        if(firebaseRemoteConfig.getInfo().getConfigSettings()
                .isDeveloperModeEnabled()){
            cacheExpiration = 0;
        }

        firebaseRemoteConfig.fetch(cacheExpiration)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        firebaseRemoteConfig.activateFetched();
                        applyRetrievalLengthLimit();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG, "Failed to fetch config " + e.getMessage());
                        applyRetrievalLengthLimit();
                    }
                });
    }

    private void applyRetrievalLengthLimit(){
        Long friendly_msg_length = firebaseRemoteConfig.getLong("friendly_msg_length");
        mMessageEditText.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(friendly_msg_length.intValue())});
        Log.d(TAG, String.format("Message length limit is %d", friendly_msg_length));
    }

    @Override
    public void onStart() {
        super.onStart();
        // Check if user is signed in.
        // TODO: Add code to check if user is signed in.
    }

    @Override
    public void onPause() {
        super.onPause();
        if(adView != null) adView.pause();
    }

    @Override
    public void onResume() {
        super.onResume();
        if(adView != null) adView.resume();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(adView != null) adView.destroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()){
            case R.id.sign_out_menu:
                firebaseAuth.signOut();
                Auth.GoogleSignInApi.signOut(mGoogleApiClient);
                mUsername = ANONYMOUS;
                startActivity(new Intent(MainActivity.this, SignInActivity.class));
                finish();
                return true;
            case R.id.fresh_config_menu:
                fetchConfig();
                return true;
            case R.id.invite_menu:
                sendInvitation();
                return true;
            case R.id.crash_menu:
                FirebaseCrash.logcat(Log.ERROR, TAG, "crash caused");
                causeCrash();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void causeCrash(){
        throw new NullPointerException("Fake null pointer exception");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        // An unresolvable error has occurred and Google APIs (including Sign-In) will not
        // be available.
        Log.d(TAG, "onConnectionFailed:" + connectionResult);
        Toast.makeText(this, "Google Play Services error.", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode == REQUEST_IMAGE){
            if(resultCode == RESULT_OK){
                if(data != null){
                    final Uri uri = data.getData();
                    Log.d(TAG, "Uri: " + uri.toString());
                    FriendlyMessage friendlyMessage = new FriendlyMessage(
                            null,
                            mUsername,
                            mPhotoUrl,
                            LOADING_IMAGE_URL
                    );

                    databaseReference.child(MESSAGES_CHILD).push()
                            .setValue(friendlyMessage, new DatabaseReference.CompletionListener() {
                                @Override
                                public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
                                    if(databaseError == null){
                                        String key = databaseReference.getKey();
                                        StorageReference storageReference = FirebaseStorage.getInstance()
                                                .getReference(firebaseUser.getUid())
                                                .child(key)
                                                .child(uri.getLastPathSegment());

                                        putImageInStorage(storageReference, uri, key);
                                    }else{
                                        Log.w(TAG, "unable to write to database", databaseError.toException());
                                    }
                                }
                            });
                }
            }
        }
        else if(requestCode == REQUEST_INVITE){
            if(resultCode == RESULT_OK){
                String[] ids = AppInviteInvitation.getInvitationIds(resultCode, data);
                Log.d(TAG, "Invitations sent");
                Bundle payload = new Bundle();
                payload.putString(FirebaseAnalytics.Param.VALUE, "sent");
                firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SHARE, payload);
            }else{
                Log.w(TAG, "Failed to send invitations");
                Bundle payload = new Bundle();
                payload.putString(FirebaseAnalytics.Param.VALUE, "not sent");
                firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SHARE, payload);
            }
        }
    }

    public void putImageInStorage(StorageReference storageReference, Uri uri, final String key){
        storageReference.putFile(uri).addOnCompleteListener(new OnCompleteListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<UploadTask.TaskSnapshot> task) {
                if(task.isSuccessful()){
                    FriendlyMessage friendlyMessage =
                            new FriendlyMessage(null, mUsername, mPhotoUrl,
                                    task.getResult().getMetadata().getDownloadUrl().toString());
                    databaseReference.child(MESSAGES_CHILD).child(key)
                            .setValue(friendlyMessage);
                }else{
                    Log.w(TAG, "Image upload not successful", task.getException());
                }
            }
        });
    }

    private Indexable getMessageIndexable(FriendlyMessage friendlyMessage){
        PersonBuilder sender = Indexables.personBuilder()
                .setIsSelf(friendlyMessage.getName().equals(mUsername))
                .setName(friendlyMessage.getName())
                .setUrl(MESSAGE_URL.concat(friendlyMessage.getId() + "/sender"));

        PersonBuilder recipient = Indexables.personBuilder()
                .setName(mUsername)
                .setUrl(MESSAGE_URL.concat(friendlyMessage.getId() + "/recipient"));

        Indexable messageIndex = Indexables.messageBuilder()
                .setName(friendlyMessage.getText())
                .setUrl(MESSAGE_URL.concat(friendlyMessage.getId()))
                .setSender(sender)
                .setRecipient(recipient)
                .build();

        return messageIndex;
    }

    private Action getMessageViewAction(FriendlyMessage friendlyMessage){
        return new Action.Builder(Action.Builder.VIEW_ACTION)
                .setObject(friendlyMessage.getName(), MESSAGE_URL.concat(friendlyMessage.getId()))
                .setMetadata(new Action.Metadata.Builder().setUpload(false))
                .build();
    }

    private void sendInvitation(){
        Intent intent = new AppInviteInvitation.IntentBuilder(getString(R.string.invitation_title))
                .setMessage(getString(R.string.invitation_message))
                .setCallToActionText(getString(R.string.invitation_cta))
                .build();
        startActivityForResult(intent, REQUEST_INVITE);
    }

}
