package com.facebook.scrumptiousmy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.facebook.FacebookRequestError;
import com.facebook.HttpMethod;
import com.facebook.Request;
import com.facebook.Response;
import com.facebook.Session;
import com.facebook.SessionState;
import com.facebook.UiLifecycleHelper;
import com.facebook.model.GraphObject;
import com.facebook.model.GraphUser;
import com.facebook.model.OpenGraphAction;
import com.facebook.widget.ProfilePictureView;

public class SelectionFragment extends Fragment {

	private static final String TAG = "SelectionFragment";
	
	private ProfilePictureView profilePictureView;
	private TextView userNameView;
	private UiLifecycleHelper uiHelper;
	private Session.StatusCallback callback = new Session.StatusCallback() {
	    @Override
	    public void call(final Session session, final SessionState state, final Exception exception) {
	        onSessionStateChange(session, state, exception);
	    }
	};
	
	// Activity code to flag an incoming activity result is due 
	// to a new permissions request
	private static final int REAUTH_ACTIVITY_CODE = 100;
	
	private ListView listView;
	private List<BaseListElement> listElements;
	private Button announceButton;
	
	// Indicates an on-going reauthorization request
	private boolean pendingAnnounce;

	// Key used in storing the pendingAnnounce flag
	private static final String PENDING_ANNOUNCE_KEY = "pendingAnnounce";

	/// List of additional write permissions being requested
	private static final List<String> PERMISSIONS = Arrays.asList("publish_actions");
	
	// Redirect URL for authentication errors requiring a user action
	private static final Uri M_FACEBOOK_URL = Uri.parse("http://m.facebook.com");
	
	private static final String POST_ACTION_PATH = "me/scrumptiousmy:eat";
	private ProgressDialog progressDialog;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		super.onCreateView(inflater, container, savedInstanceState);
		
		uiHelper = new UiLifecycleHelper(getActivity(), callback);
	    uiHelper.onCreate(savedInstanceState);
	    
		View view = inflater.inflate(R.layout.selection, container, false);
		
		// Find the user's profile picture custom view
		profilePictureView = (ProfilePictureView) view.findViewById(R.id.selection_profile_pic);
		//profilePictureView.setCropped(true);

		// Find the user's name view
		userNameView = (TextView) view.findViewById(R.id.selection_user_name);
		
		// Find the list view
		listView = (ListView) view.findViewById(R.id.selection_list);
    
	    // Set up the publish action button
	    announceButton = (Button) view.findViewById(R.id.announce_button);
	    announceButton.setOnClickListener(new View.OnClickListener() {
	        @Override
	        public void onClick(View view) {
	        	handleAnnounce();
	        }
	    });
	    
	    // Initialize the button and list view
	    init(savedInstanceState);
	    
		return view;
	}
	
	private void init(Bundle savedInstanceState) {
	    // Disable the button initially
	    announceButton.setEnabled(false);

	    // Set up the list view items, based on a list of
	    // BaseListElement items
	    listElements = new ArrayList<BaseListElement>();
	    // Add an item for the meal picker
	    listElements.add(new EatListElement(0));
	    // Add an item for the friend picker
	    listElements.add(new PeopleListElement(2));

	    if (savedInstanceState != null) {
	        // Restore the state for each list element
	        for (BaseListElement listElement : listElements) {
	            listElement.restoreState(savedInstanceState);
	        }
	        // Restore the pending flag
	        pendingAnnounce = savedInstanceState.getBoolean(
	                PENDING_ANNOUNCE_KEY, false);
	    }

	    // Set the list view adapter
	    listView.setAdapter(new ActionListAdapter(getActivity(), 
	                        R.id.selection_list, listElements));

	    // Check for an open session
	    Session session = Session.getActiveSession();
	    if (session != null && session.isOpened()) {
	        // Get the user's data
	        makeMeRequest(session);
	    }
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
	    super.onActivityResult(requestCode, resultCode, data);
	    if (requestCode == REAUTH_ACTIVITY_CODE) {
	      uiHelper.onActivityResult(requestCode, resultCode, data);
	    } else if (resultCode == Activity.RESULT_OK &&
	    	requestCode >= 0 && requestCode < listElements.size()) {
	    	    listElements.get(requestCode).onActivityResult(data);
	    }
	}
	
	@Override
	public void onResume() {
	    super.onResume();
	    uiHelper.onResume();
	}

	@Override
	public void onSaveInstanceState(Bundle bundle) {
	    super.onSaveInstanceState(bundle);
	    for (BaseListElement listElement : listElements) {
	        listElement.onSaveInstanceState(bundle);
	    }
	    bundle.putBoolean(PENDING_ANNOUNCE_KEY, pendingAnnounce);
	    uiHelper.onSaveInstanceState(bundle);
	}

	@Override
	public void onPause() {
	    super.onPause();
	    uiHelper.onPause();
	}

	@Override
	public void onDestroy() {
	    super.onDestroy();
	    uiHelper.onDestroy();
	}
	
	private void makeMeRequest(final Session session) {
	    // Make an API call to get user data and define a 
	    // new callback to handle the response.
	    Request request = Request.newMeRequest(session, 
	            new Request.GraphUserCallback() {
	        @Override
	        public void onCompleted(GraphUser user, Response response) {
	            // If the response is successful
	            if (session == Session.getActiveSession()) {
	                if (user != null) {
	                    // Set the id for the ProfilePictureView
	                    // view that in turn displays the profile picture.
	                    profilePictureView.setProfileId(user.getId());
	                    // Set the Textview's text to the user's name.
	                    userNameView.setText(user.getName());
	                }
	            }
	            if (response.getError() != null) {
	            	// Handle errors found when fetching user data.
	                handleError(response.getError());
	            }
	        }
	    });
	    request.executeAsync();
	}
	
	private void onSessionStateChange(final Session session, SessionState state, Exception exception) {
	    if (session != null && session.isOpened()) {
	        if (state.equals(SessionState.OPENED_TOKEN_UPDATED)) {
	            // Session updated with new permissions
	            // so try publishing once more.
	            tokenUpdated();
	        } else {
	            // Get the user's data.
	            makeMeRequest(session);
	        }
	     }
	}
	
	private class ActionListAdapter extends ArrayAdapter<BaseListElement> {
	    private List<BaseListElement> listElements;

	    public ActionListAdapter(Context context, int resourceId, 
	                             List<BaseListElement> listElements) {
	        super(context, resourceId, listElements);
	        this.listElements = listElements;
	        // Set up as an observer for list item changes to
	        // refresh the view.
	        for (int i = 0; i < listElements.size(); i++) {
	            listElements.get(i).setAdapter(this);
	        }
	    }

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View view = convertView;
			if (view == null) {
				LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				view = inflater.inflate(R.layout.listitem, null);
			}
			
			BaseListElement listElement = listElements.get(position);
			if (listElement != null) {
				view.setOnClickListener(listElement.getOnClickListener());
				ImageView icon = (ImageView) view.findViewById(R.id.icon);
				TextView text1 = (TextView) view.findViewById(R.id.text1);
				TextView text2 = (TextView) view.findViewById(R.id.text2);
				if (icon != null) {
					icon.setImageDrawable(listElement.getIcon());
				}
				if (text1 != null) {
					text1.setText(listElement.getText1());
				}
				if (text2 != null) {
					text2.setText(listElement.getText2());
				}
			}
			return view;
		}
		
	}
	
	private class PeopleListElement extends BaseListElement {
		private List<GraphUser> selectedUsers;
		private static final String FRIENDS_KEY = "friends";

	    public PeopleListElement(int requestCode) {
	        super(getActivity().getResources().getDrawable(R.drawable.add_friends),
	              getActivity().getResources().getString(R.string.action_people),
	              getActivity().getResources().getString(R.string.action_people_default),
	              requestCode);
	    }

	    @Override
	    protected View.OnClickListener getOnClickListener() {
	        return new View.OnClickListener() {
	            @Override
	            public void onClick(View view) {
	            	startPickerActivity(PickerActivity.FRIEND_PICKER, getRequestCode());
	            }
	        };
	    }
	    
	    @Override
	    protected void onActivityResult(Intent data) {
	        selectedUsers = ((ScrumptiousApplication) getActivity()
	                 .getApplication())
	                 .getSelectedUsers();
	        setUsersText();
	        notifyDataChanged();
	    }
	    
	    @Override
	    protected void onSaveInstanceState(Bundle bundle) {
	        if (selectedUsers != null) {
	            bundle.putByteArray(FRIENDS_KEY,
	                    getByteArray(selectedUsers));
	        }   
	    } 
	    
	    @Override
	    protected boolean restoreState(Bundle savedState) {
	        byte[] bytes = savedState.getByteArray(FRIENDS_KEY);
	        if (bytes != null) {
	            selectedUsers = restoreByteArray(bytes);
	            setUsersText();
	            return true;
	        }   
	        return false;
	    } 
	    
	    private List<GraphUser> restoreByteArray(byte[] bytes) {
	        try {
	            @SuppressWarnings("unchecked")
	            List<String> usersAsString =
	                    (List<String>) (new ObjectInputStream
	                                    (new ByteArrayInputStream(bytes)))
	                                    .readObject();
	            if (usersAsString != null) {
	                List<GraphUser> users = new ArrayList<GraphUser>
	                (usersAsString.size());
	                for (String user : usersAsString) {
	                    GraphUser graphUser = GraphObject.Factory
	                    .create(new JSONObject(user), 
	                                    GraphUser.class);
	                    users.add(graphUser);
	                }   
	                return users;
	            }   
	        } catch (ClassNotFoundException e) {
	            Log.e(TAG, "Unable to deserialize users.", e); 
	        } catch (IOException e) {
	            Log.e(TAG, "Unable to deserialize users.", e); 
	        } catch (JSONException e) {
	            Log.e(TAG, "Unable to deserialize users.", e); 
	        }   
	        return null;
	    }
	    
	    public void onClick(View view) {
	        startPickerActivity(PickerActivity.FRIEND_PICKER, getRequestCode());
	    }
	    
	    private void setUsersText() {
	        String text = null;
	        if (selectedUsers != null) {
	                // If there is one friend
	            if (selectedUsers.size() == 1) {
	                text = String.format(getResources()
	                        .getString(R.string.single_user_selected),
	                        selectedUsers.get(0).getName());
	            } else if (selectedUsers.size() == 2) {
	                // If there are two friends 
	                text = String.format(getResources()
	                        .getString(R.string.two_users_selected),
	                        selectedUsers.get(0).getName(), 
	                        selectedUsers.get(1).getName());
	            } else if (selectedUsers.size() > 2) {
	                // If there are more than two friends 
	                text = String.format(getResources()
	                        .getString(R.string.multiple_users_selected),
	                        selectedUsers.get(0).getName(), 
	                        (selectedUsers.size() - 1));
	            }   
	        }   
	        if (text == null) {
	            // If no text, use the placeholder text
	            text = getResources()
	            .getString(R.string.action_people_default);
	        }   
	        // Set the text in list element. This will notify the 
	        // adapter that the data has changed to
	        // refresh the list view.
	        setText2(text);
	    }
	    
	    private byte[] getByteArray(List<GraphUser> users) {
	        // convert the list of GraphUsers to a list of String 
	        // where each element is the JSON representation of the 
	        // GraphUser so it can be stored in a Bundle
	        List<String> usersAsString = new ArrayList<String>(users.size());

	        for (GraphUser user : users) {
	            usersAsString.add(user.getInnerJSONObject().toString());
	        }   
	        try {
	            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
	            new ObjectOutputStream(outputStream).writeObject(usersAsString);
	            return outputStream.toByteArray();
	        } catch (IOException e) {
	            Log.e(TAG, "Unable to serialize users.", e); 
	        }   
	        return null;
	    }

		@Override
		protected void populateOGAction(OpenGraphAction action) {
		    if (selectedUsers != null) {
		        action.setTags(selectedUsers);
		    }   
		}   
	}
	
	private void startPickerActivity(Uri data, int requestCode) {
	     Intent intent = new Intent();
	     intent.setData(data);
	     intent.setClass(getActivity(), PickerActivity.class);
	     startActivityForResult(intent, requestCode);
	 }
	
	private class EatListElement extends BaseListElement {

	    private final String[] foodChoices;
	    private final String[] foodUrls;
		private String foodChoiceUrl = null;
		private String foodChoice = null;
		private static final String FOOD_KEY = "food";
		private static final String FOOD_URL_KEY = "food_url";
		
	    public EatListElement(int requestCode) {
	        super(getActivity().getResources()
	                .getDrawable(R.drawable.add_food),
	              getActivity().getResources()
	                .getString(R.string.action_eating),
	              getActivity().getResources()
	                .getString(R.string.action_eating_default),
	              requestCode);
	        foodChoices = getActivity().getResources()
	                          .getStringArray(R.array.food_types);
	        foodUrls = getActivity().getResources()
	                       .getStringArray(R.array.food_og_urls);
	    }

	    @Override
	    protected View.OnClickListener getOnClickListener() {
	        return new View.OnClickListener() {
	            @Override
	            public void onClick(View view) {
	            	showMealOptions();
	            }
	        };
	    }
	    
	    private void showMealOptions() {
	        String title = getActivity().getResources().getString(R.string.select_meal);
	        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
	        builder.setTitle(title).
	                setCancelable(true).
	                setItems(foodChoices, new DialogInterface.OnClickListener() {
	                    @Override
	                    public void onClick(DialogInterface dialogInterface, int i) {
	                    	foodChoice = foodChoices[i];
	                        foodChoiceUrl = foodUrls[i];
	                        setFoodText();
	                        notifyDataChanged();
	                    }   
	                }); 
	        builder.show();
	    }
	    
	    private void setFoodText() {
	        if (foodChoice != null && foodChoiceUrl != null) {
	            setText2(foodChoice);
	            announceButton.setEnabled(true);
	        } else {
	            setText2(getActivity().getResources()
	                    .getString(R.string.action_eating_default));
	            announceButton.setEnabled(false);
	        }   
	    }
	    
	    @Override
	    protected void onSaveInstanceState(Bundle bundle) {
	        if (foodChoice != null && foodChoiceUrl != null) {
	            bundle.putString(FOOD_KEY, foodChoice);
	            bundle.putString(FOOD_URL_KEY, foodChoiceUrl);
	        }   
	    }
	    
	    @Override
	    protected boolean restoreState(Bundle savedState) {
	        String food = savedState.getString(FOOD_KEY);
	        String foodUrl = savedState.getString(FOOD_URL_KEY);
	        if (food != null && foodUrl != null) {
	            foodChoice = food;
	            foodChoiceUrl = foodUrl;
	            setFoodText();
	            return true;
	        }   
	        return false;
	    }
	    
	    @Override
	    protected void populateOGAction(OpenGraphAction action) {
	        if (foodChoiceUrl != null) {
	            EatAction eatAction = action.cast(EatAction.class);
	            MealGraphObject meal = 
	                GraphObject.Factory.create(MealGraphObject.class);
	            meal.setUrl(foodChoiceUrl);
	            eatAction.setMeal(meal);
	        }   
	    } 
	}
	
	private interface MealGraphObject extends GraphObject {
	    // A URL
	    public String getUrl();
	    public void setUrl(String url);

	    // An ID
	    public String getId();
	    public void setId(String id);
	}
	
	private interface EatAction extends OpenGraphAction {
	    // The meal object
	    public MealGraphObject getMeal();
	    public void setMeal(MealGraphObject meal);
	}
	
	private void requestPublishPermissions(Session session) {
	    if (session != null) {
	        Session.NewPermissionsRequest newPermissionsRequest = 
	            new Session.NewPermissionsRequest(this, PERMISSIONS).
	                setRequestCode(REAUTH_ACTIVITY_CODE);
	        session.requestNewPublishPermissions(newPermissionsRequest);
	    }
	}
	
	private void handleAnnounce() {
		pendingAnnounce = false;
		Session session = Session.getActiveSession();

		if (session == null || !session.isOpened()) {
			return;
		}

		List<String> permissions = session.getPermissions();
		if (!permissions.containsAll(PERMISSIONS)) {
			pendingAnnounce = true;
			requestPublishPermissions(session);
			return;
		}

		// Show a progress dialog because sometimes the 
		// requests can take a while. This dialog contains
		// a text message
		progressDialog = ProgressDialog.show(getActivity(), "", 
				getActivity().getResources()
				.getString(R.string.progress_dialog_text), true);

		// Run this in a background thread since we don't want to 
		// block the main thread. Create a new AsyncTask that returns
		// a Response object
		AsyncTask<Void, Void, Response> task = 
				new AsyncTask<Void, Void, Response>() {

			@Override
			protected Response doInBackground(Void... voids) {
				// Create an eat action
				EatAction eatAction = 
						GraphObject.Factory.create(EatAction.class);
				// Populate the action with the POST parameters:
				// the meal, friends, and place info
				for (BaseListElement element : listElements) {
					element.populateOGAction(eatAction);
				}   
				// Set up a request with the active session, set up
				// an HTTP POST to the eat action endpoint
				Request request = new Request(Session.getActiveSession(),
						POST_ACTION_PATH, null, HttpMethod.POST);
				// Add the post parameter, the eat action
				request.setGraphObject(eatAction);
				// Execute the request synchronously in the background
				// and return the response.
				return request.executeAndWait();
			}   

			@Override
			protected void onPostExecute(Response response) {
				// When the task completes, process
				// the response on the main thread
				onPostActionResponse(response);
			}   
		};  

	 // Execute the task
	 task.execute();

	}
	
	private void tokenUpdated() {
	    // Check if a publish action is in progress
	    // awaiting a successful reauthorization
	    if (pendingAnnounce) {
	        // Publish the action
	        handleAnnounce();
	    }
	}
	
	private void handleError(FacebookRequestError error) {
	    DialogInterface.OnClickListener listener = null;
	    String dialogBody = null;

	    if (error == null) {
	        // There was no response from the server.
	        dialogBody = getString(R.string.error_dialog_default_text);
	    } else {
	        switch (error.getCategory()) {
	            case AUTHENTICATION_RETRY:
	                // Tell the user what happened by getting the
	                // message id, and retry the operation later.
	                String userAction = (error.shouldNotifyUser()) ? "" :
	                        getString(error.getUserActionMessageId());
	                dialogBody = getString(R.string.error_authentication_retry, 
	                                       userAction);
	                listener = new DialogInterface.OnClickListener() {
	                    @Override
	                    public void onClick(DialogInterface dialogInterface, 
	                                        int i) {
	                        // Take the user to the mobile site.
	                        Intent intent = new Intent(Intent.ACTION_VIEW, 
	                                                   M_FACEBOOK_URL);
	                        startActivity(intent);
	                    }
	                };
	                break;

	            case AUTHENTICATION_REOPEN_SESSION:
	                // Close the session and reopen it.
	                dialogBody = 
	                    getString(R.string.error_authentication_reopen);
	                listener = new DialogInterface.OnClickListener() {
	                    @Override
	                    public void onClick(DialogInterface dialogInterface, 
	                                        int i) {
	                        Session session = Session.getActiveSession();
	                        if (session != null && !session.isClosed()) {
	                            session.closeAndClearTokenInformation();
	                        }
	                    }
	                };
	                break;

	            case PERMISSION:
	                // A permissions-related error
	                dialogBody = getString(R.string.error_permission);
	                listener = new DialogInterface.OnClickListener() {
	                    @Override
	                    public void onClick(DialogInterface dialogInterface, 
	                                        int i) {
	                        pendingAnnounce = true;
	                        // Request publish permission
	                        requestPublishPermissions(Session.getActiveSession());
	                    }
	                };
	                break;

	            case SERVER:
	            case THROTTLING:
	                // This is usually temporary, don't clear the fields, and
	                // ask the user to try again.
	                dialogBody = getString(R.string.error_server);
	                break;

	            case BAD_REQUEST:
	                // This is likely a coding error, ask the user to file a bug.
	                dialogBody = getString(R.string.error_bad_request, 
	                                       error.getErrorMessage());
	                break;

	            case OTHER:
	            case CLIENT:
	            default:
	                // An unknown issue occurred, this could be a code error, or
	                // a server side issue, log the issue, and either ask the
	                // user to retry, or file a bug.
	                dialogBody = getString(R.string.error_unknown, 
	                                       error.getErrorMessage());
	                break;
	        }
	    }

	    // Show the error and pass in the listener so action
	    // can be taken, if necessary.
	    new AlertDialog.Builder(getActivity())
	            .setPositiveButton(R.string.error_dialog_button_text, listener)
	            .setTitle(R.string.error_dialog_title)
	            .setMessage(dialogBody)
	            .show();
	}
	
	private interface PostResponse extends GraphObject {
	    String getId();
	}
	
	private void onPostActionResponse(Response response) {
		if (progressDialog != null) {
	        progressDialog.dismiss();
	        progressDialog = null;
	    }
		
	    if (getActivity() == null) {
	        // if the user removes the app from the website,
	        // then a request will have caused the session to 
	        // close (since the token is no longer valid),
	        // which means the splash fragment will be shown 
	        // rather than this one, causing activity to be null. 
	        // If the activity is null, then we cannot
	        // show any dialogs, so we return.
	        return;
	    }

	    PostResponse postResponse = 
	        response.getGraphObjectAs(PostResponse.class);

	    if (postResponse != null && postResponse.getId() != null) {
	        String dialogBody = String.format(getString(
	                                R.string.result_dialog_text_with_id), 
	                                postResponse.getId());
	        new AlertDialog.Builder(getActivity())
	                .setPositiveButton(R.string.result_dialog_button_text, 
	                                   null)
	                .setTitle(R.string.result_dialog_title)
	                .setMessage(dialogBody)
	                .show();
	        
	        // Reset the button and list view
	        init(null);
	    } else {
	        handleError(response.getError());
	    }
	}
}
