package org.servalproject.ui;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.servalproject.Control;
import org.servalproject.PreparationWizard;
import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.ServalBatPhoneApplication.State;
import org.servalproject.system.NetworkConfiguration;
import org.servalproject.system.NetworkManager;
import org.servalproject.system.NetworkManager.OnNetworkChange;
import org.servalproject.system.WifiAdhocControl;
import org.servalproject.system.WifiAdhocNetwork;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ListView;

public class Networks extends Activity implements OnNetworkChange,
		OnItemClickListener, OnClickListener {
	private ArrayAdapter<NetworkConfiguration> adapter;
	private List<NetworkConfiguration> data = new ArrayList<NetworkConfiguration>();
	private ListView listView;
	private ServalBatPhoneApplication app;
	private NetworkManager nm;
	private CheckBox enabled;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setContentView(R.layout.networks);
		this.listView = (ListView) this.findViewById(R.id.listView);
		this.enabled = (CheckBox) this.findViewById(R.id.enabled);

		this.app = (ServalBatPhoneApplication)this.getApplication();
		this.nm = NetworkManager.getNetworkManager(app);

		listView.setOnItemClickListener(this);
		enabled.setOnClickListener(this);
	}

	private void stateChanged(State state) {
		enabled.setEnabled(state == State.On || state == State.Off);
		enabled.setText(state.getResourceId());
		enabled.setChecked(state == State.On);
	}

	BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (action.equals(ServalBatPhoneApplication.ACTION_STATE)) {
				int stateOrd = intent.getIntExtra(
						ServalBatPhoneApplication.EXTRA_STATE, 0);
				State state = State.values()[stateOrd];
				stateChanged(state);
			}
		}

	};

	@Override
	protected void onResume() {
		super.onResume();
		nm.setNetworkChangeListener(this);
		this.onNetworkChange();

		IntentFilter filter = new IntentFilter();
		filter.addAction(ServalBatPhoneApplication.ACTION_STATE);
		this.registerReceiver(receiver, filter);

		stateChanged(app.getState());
	}

	@Override
	protected void onPause() {
		super.onPause();
		this.unregisterReceiver(receiver);
	}

	@Override
	public void onNetworkChange() {
		List<NetworkConfiguration> networks = nm.getNetworks();
		data.clear();
		data.addAll(networks);

		if (adapter==null){
			adapter = new ArrayAdapter<NetworkConfiguration>(this,
					android.R.layout.simple_list_item_1, data);
			listView.setAdapter(adapter);
		}else{
			adapter.notifyDataSetChanged();
		}
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {
		NetworkConfiguration config = adapter.getItem(position);

		if (config instanceof WifiAdhocNetwork) {
			if (!WifiAdhocControl.isAdhocSupported()) {
				// Clear out old attempt_ files
				File varDir = new File("/data/data/org.servalproject/var/");
				if (varDir.isDirectory())
					for (File f : varDir.listFiles()) {
						if (!f.getName().startsWith("attempt_"))
							continue;
						f.delete();
					}
				// Re-run wizard
				Intent prepintent = new Intent(this, PreparationWizard.class);
				prepintent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				startActivity(prepintent);
				return;
			}
		}
		try {
			nm.connect(config);
		} catch (IOException e) {
			Log.e("Networks", e.getMessage(), e);
			app.displayToastMessage(e.getMessage());
		}
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.enabled:
			// toggle enabled
			Intent serviceIntent = new Intent(Networks.this, Control.class);
			switch (app.getState()) {
			case Off:
				startService(serviceIntent);
				break;
			case On:
				this.stopService(serviceIntent);
				break;
			}

			break;
		}
	}
}
