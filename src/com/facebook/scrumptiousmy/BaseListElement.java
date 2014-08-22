package com.facebook.scrumptiousmy;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.BaseAdapter;

import com.facebook.model.OpenGraphAction;

public abstract class BaseListElement {
	private Drawable icon;
	private String text1;
	private String text2;
	private int requestCode;
	private BaseAdapter adapter;	
	
	protected void onActivityResult(Intent data) {}

	protected void onSaveInstanceState(Bundle bundle) {}

	protected boolean restoreState(Bundle savedState) {
	    return false;
	}

	protected void notifyDataChanged() {
	    adapter.notifyDataSetChanged();
	}
	
	protected abstract View.OnClickListener getOnClickListener();
	
	protected abstract void populateOGAction(OpenGraphAction action);
	
	public BaseListElement(Drawable icon, String text1, String text2,
			int requestCode) {
		super();
		this.icon = icon;
		this.text1 = text1;
		this.text2 = text2;
		this.requestCode = requestCode;
	}
	
	public void setAdapter(BaseAdapter adapter) {
		this.adapter = adapter;
	}

	public int getRequestCode() {
		return requestCode;
	}
	public Drawable getIcon() {
		return icon;
	}
	public String getText1() {
		return text1;
	}
	public void setText1(String text1) {
		if (adapter != null) {
		    adapter.notifyDataSetChanged();
		}
		this.text1 = text1;
	}
	public String getText2() {
		if (adapter != null) {
		    adapter.notifyDataSetChanged();
		}
		return text2;
	}
	public void setText2(String text2) {
		this.text2 = text2;
	}
	
}
