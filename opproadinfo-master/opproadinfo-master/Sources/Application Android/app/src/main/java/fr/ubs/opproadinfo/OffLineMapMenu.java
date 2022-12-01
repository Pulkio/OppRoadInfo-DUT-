package fr.ubs.opproadinfo;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.mapbox.mapboxsdk.offline.OfflineManager;
import com.mapbox.mapboxsdk.offline.OfflineRegion;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;


/**
 * The type Off line map menu.
 */
public class OffLineMapMenu extends AppCompatActivity implements View.OnClickListener, OfflineRegion.OfflineRegionDeleteCallback, OfflineRegion.OfflineRegionUpdateMetadataCallback, TextWatcher {

    private ListView offLineMapList;
    private AlertDialog nameMapDialog;
    private ArrayAdapter<String> adapter;
    private ArrayList<String> nameList;
    private OfflineRegion[] offlineRegions;
    private static final int DOWNLOAD_MODE = 1;
    private static final int MODIFY_MODE = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_off_line_map_menu);

        offLineMapList = findViewById(R.id.offlineMapList);
        registerForContextMenu(offLineMapList);
        FloatingActionButton downloadMapButton = findViewById(R.id.downloadMapButton);
        downloadMapButton.setOnClickListener(this);
        listOfflineMaps();
    }

    /**
     * Load all the offline maps and put it in list view
     */
    public void listOfflineMaps() {
        OfflineManager offlineManager = OfflineManager.getInstance(this);

        offlineManager.listOfflineRegions(new OfflineManager.ListOfflineRegionsCallback() {
            @Override
            public void onList(OfflineRegion[] offlineRegions) {
                OffLineMapMenu.this.nameList = new ArrayList<>();
                OffLineMapMenu.this.offlineRegions = offlineRegions;
                for (OfflineRegion offlineRegion : offlineRegions) {

                    try {
                        String region = new String(offlineRegion.getMetadata());
                        JSONObject json = new JSONObject(region);
                        nameList.add(json.getString(DownloadMapActivity.JSON_FIELD_REGION_NAME));

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

                adapter = new ArrayAdapter<>(OffLineMapMenu.this, android.R.layout.simple_list_item_1, nameList);
                offLineMapList.setAdapter(adapter);

                if (nameList.isEmpty()) {
                    findViewById(R.id.offlineMapEmptyText).setVisibility(View.VISIBLE);
                    findViewById(R.id.offlineMapArrow).setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(String error) {
            }
        });
    }


    /**
     * Download the offline map with the name given in parameter
     * @param mapName name of the map to download
     */
    private void downloadMap(String mapName) {
        Intent intent = new Intent(OffLineMapMenu.this, DownloadMapActivity.class);
        intent.putExtra("regionName", mapName);
        startActivity(intent);
        finish();
    }

    /**
     * Rename an offline map.
     * @param mapName new map name
     * @param positionList position in the list of the map to rename
     */
    private void renameMap(String mapName, int positionList) {
        byte[] metadata;
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put(DownloadMapActivity.JSON_FIELD_REGION_NAME, mapName);
            String json = jsonObject.toString();
            metadata = json.getBytes(DownloadMapActivity.JSON_CHARSET);
            nameList.set(positionList, mapName);
        } catch (Exception exception) {
            metadata = null;
        }

        assert metadata != null;
        offlineRegions[positionList].updateMetadata(metadata, OffLineMapMenu.this);
    }

    /**
     * Delete an offline map with its position in the list
     * @param position of the map to delete
     */
    private void deleteMap(int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.delete);
        builder.setMessage(getResources().getString(R.string.deleteQuestion) + this.nameList.get(position) + getResources().getString(R.string.questionMark));
        builder.setPositiveButton(R.string.delete, (dialog, which) -> {
            offlineRegions[position].delete(OffLineMapMenu.this);
            nameList.remove(position);
            adapter.notifyDataSetChanged();

            if (nameList.isEmpty()) {
                findViewById(R.id.offlineMapEmptyText).setVisibility(View.VISIBLE);
                findViewById(R.id.offlineMapArrow).setVisibility(View.VISIBLE);
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    /**
     * Create an adaptive alert dialog. Must be a download dialog or rename map dialog.
     * @param mode of the dialog. Must be MODIFY_MODE or DOWNLOAD_MODE
     * @param positionList position of the clicked item in the maps list
     */
    private void createAlertDialog(int mode, int positionList) {
        AlertDialog.Builder regionNameBuilder = new AlertDialog.Builder(OffLineMapMenu.this);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(50, 50, 50, 50);

        EditText regionNameInput = new EditText(OffLineMapMenu.this);
        regionNameInput.setInputType(InputType.TYPE_CLASS_TEXT);

        if (mode == MODIFY_MODE) {
            try {
                String metadata = new String(offlineRegions[positionList].getMetadata());
                JSONObject json = new JSONObject(metadata);
                String regionName = json.getString(DownloadMapActivity.JSON_FIELD_REGION_NAME);

                regionNameInput.setText(regionName);
                regionNameBuilder.setTitle(R.string.modifyNameMap);

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        else{
            regionNameInput.setHint(R.string.myMap);
            regionNameBuilder.setTitle(R.string.enterYourMapName);
        }

        container.addView(regionNameInput, params);
        regionNameBuilder.setView(container);
        regionNameBuilder.setNegativeButton(R.string.cancel, null);

        regionNameBuilder.setPositiveButton(R.string.ok, (dialog, which) -> {
            String mapName = regionNameInput.getText().toString().trim();

            if (!mapName.equals("")) {
                if (mode ==DOWNLOAD_MODE)
                    downloadMap(mapName);

                else
                    renameMap(mapName, positionList);
            }
        });

        regionNameInput.addTextChangedListener(this);

        OffLineMapMenu.this.nameMapDialog = regionNameBuilder.show();
        OffLineMapMenu.this.nameMapDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);

        //Set the focus on the edit text
        regionNameInput.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(regionNameInput, InputMethodManager.SHOW_FORCED);
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        int position = info.position;

        switch (item.getItemId()) {
            case R.id.deleteMapItem:
                deleteMap(position);
                return true;
            case R.id.modifyMapItem:
                createAlertDialog(MODIFY_MODE, position);
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    @Override
    public void onClick(View v) {
        createAlertDialog(DOWNLOAD_MODE, -1);
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        OffLineMapMenu.this.nameMapDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!s.toString().trim().equals(""));
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        getMenuInflater().inflate(R.menu.list_map_menu, menu);
    }

    @Override
    public void onDelete() {}

    @Override
    public void onUpdate(byte[] metadata) {
        OffLineMapMenu.this.adapter.notifyDataSetChanged();
    }

    @Override
    public void onError(String error) {}

    @Override
    public void afterTextChanged(Editable s) {}

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
}