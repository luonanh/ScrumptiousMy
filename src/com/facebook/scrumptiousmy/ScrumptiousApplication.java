package com.facebook.scrumptiousmy;

import java.util.List;

import android.app.Application;

import com.facebook.model.GraphUser;

public class ScrumptiousApplication extends Application {
	private List<GraphUser> selectedUsers;
	
	public List<GraphUser> getSelectedUsers() {
	    return selectedUsers;
	}

	public void setSelectedUsers(List<GraphUser> users) {
	    selectedUsers = users;
	}
}
