/*
 * Copyright (C) 2019 Veli Tasalı
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package com.genonbeta.TrebleShot.fragment;

import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import com.genonbeta.TrebleShot.R;
import com.genonbeta.TrebleShot.activity.FilePickerActivity;
import com.genonbeta.TrebleShot.adapter.TransferListAdapter;
import com.genonbeta.TrebleShot.adapter.TransferListAdapter.StorageStatusItem;
import com.genonbeta.TrebleShot.app.Activity;
import com.genonbeta.TrebleShot.app.EditableListFragment;
import com.genonbeta.TrebleShot.app.GroupEditableListFragment;
import com.genonbeta.TrebleShot.database.Kuick;
import com.genonbeta.TrebleShot.dialog.ChooseAssigneeDialog;
import com.genonbeta.TrebleShot.dialog.DialogUtils;
import com.genonbeta.TrebleShot.dialog.TransferInfoDialog;
import com.genonbeta.TrebleShot.object.ShowingAssignee;
import com.genonbeta.TrebleShot.object.TransferGroup;
import com.genonbeta.TrebleShot.object.TransferObject;
import com.genonbeta.TrebleShot.ui.callback.TitleProvider;
import com.genonbeta.TrebleShot.util.AppUtils;
import com.genonbeta.TrebleShot.util.TransferUtils;
import com.genonbeta.TrebleShot.widget.GroupEditableListAdapter;
import com.genonbeta.android.framework.object.Selectable;
import com.genonbeta.android.framework.ui.PerformerMenu;
import com.genonbeta.android.framework.util.actionperformer.IPerformerEngine;
import com.genonbeta.android.framework.util.actionperformer.PerformerEngineProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class TransferListFragment extends GroupEditableListFragment<TransferListAdapter.GenericItem,
        GroupEditableListAdapter.GroupViewHolder, TransferListAdapter> implements TitleProvider,
        Activity.OnBackPressedListener
{
    public static final String TAG = "TransferListFragment";

    public static final String ARG_DEVICE_ID = "argDeviceId";
    public static final String ARG_GROUP_ID = "argGroupId";
    public static final String ARG_TYPE = "argType";
    public static final String ARG_PATH = "argPath";

    public static final int REQUEST_CHOOSE_FOLDER = 1;

    private TransferGroup mHeldGroup;
    private String mLastKnownPath;

    private BroadcastReceiver mReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            if (Kuick.ACTION_DATABASE_CHANGE.equals(intent.getAction())) {
                Kuick.BroadcastData data = Kuick.toData(intent);
                if (Kuick.TABLE_TRANSFER.equals(data.tableName) || Kuick.TABLE_TRANSFERGROUP.equals(data.tableName))
                    refreshList();
            }
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setFilteringSupported(true);
        setDefaultOrderingCriteria(TransferListAdapter.MODE_SORT_ORDER_ASCENDING);
        setDefaultSortingCriteria(TransferListAdapter.MODE_SORT_BY_NAME);
        setDefaultGroupingCriteria(TransferListAdapter.MODE_GROUP_BY_DEFAULT);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
    {
        super.onViewCreated(view, savedInstanceState);

        setListAdapter(new TransferListAdapter(this, this));
        setEmptyListImage(R.drawable.ic_compare_arrows_white_24dp);

        Bundle args = getArguments();
        if (args != null && args.containsKey(ARG_GROUP_ID)) {
            goPath(args.getString(ARG_PATH), args.getLong(ARG_GROUP_ID), args.getString(ARG_DEVICE_ID),
                    args.getString(ARG_TYPE));
        }
    }

    @Nullable
    @Override
    public PerformerMenu onCreatePerformerMenu(Context context)
    {
        return new PerformerMenu(context, new SelectionCallback(getActivity(), this));
    }

    @Override
    public void onResume()
    {
        super.onResume();
        requireContext().registerReceiver(mReceiver, new IntentFilter(Kuick.ACTION_DATABASE_CHANGE));
    }

    @Override
    public void onPause()
    {
        super.onPause();
        requireContext().unregisterReceiver(mReceiver);
    }

    @Override
    public int onGridSpanSize(int viewType, int currentSpanSize)
    {
        return viewType == TransferListAdapter.VIEW_TYPE_REPRESENTATIVE ? currentSpanSize
                : super.onGridSpanSize(viewType, currentSpanSize);
    }

    @Override
    public boolean onDefaultClickAction(GroupEditableListAdapter.GroupViewHolder holder)
    {
        try {
            final TransferObject transferObject = getAdapter().getItem(holder);
            new TransferInfoDialog(requireActivity(), getTransferGroup(), transferObject,
                    getAdapter().getDeviceId()).show();

            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    @Override
    protected void onListRefreshed()
    {
        super.onListRefreshed();

        String pathOnTrial = getAdapter().getPath();

        if (!(mLastKnownPath == null && getAdapter().getPath() == null)
                && (mLastKnownPath != null && !mLastKnownPath.equals(pathOnTrial)))
            getListView().scrollToPosition(0);

        mLastKnownPath = pathOnTrial;
    }

    @Override
    public boolean onBackPressed()
    {
        String path = getAdapter().getPath();

        if (path == null)
            return false;

        int slashPos = path.lastIndexOf(File.separator);

        goPath(slashPos == -1 && path.length() > 0 ? null : path.substring(0, slashPos));

        return true;
    }

    public void changeSavePath(String initialPath)
    {
        startActivityForResult(new Intent(getActivity(), FilePickerActivity.class)
                .setAction(FilePickerActivity.ACTION_CHOOSE_DIRECTORY)
                .putExtra(FilePickerActivity.EXTRA_START_PATH, initialPath)
                .putExtra(FilePickerActivity.EXTRA_ACTIVITY_TITLE, getString(R.string.butn_saveTo)), REQUEST_CHOOSE_FOLDER);
    }


    @Override
    public CharSequence getDistinctiveTitle(Context context)
    {
        return context.getString(R.string.text_transfers);
    }

    @Override
    public boolean performLayoutClick(GroupEditableListAdapter.GroupViewHolder holder)
    {
        try {
            final TransferObject transferObject = getAdapter().getItem(holder);

            if (transferObject instanceof TransferListAdapter.DetailsTransferFolder) {
                final List<ShowingAssignee> list = TransferUtils.loadAssigneeList(getContext(),
                        getTransferGroup().id, null);

                DialogInterface.OnClickListener listClickListener = (dialog, which) -> {
                    getAdapter().setAssignee(list.get(which));
                    getAdapter().setPath(getAdapter().getPath());
                    refreshList();
                };

                DialogInterface.OnClickListener noLimitListener = (dialog, which) -> {
                    getAdapter().setAssignee(null);
                    getAdapter().setPath(getAdapter().getPath());
                    refreshList();
                };

                ChooseAssigneeDialog dialog = new ChooseAssigneeDialog(requireActivity(), list, listClickListener);

                dialog.setTitle(R.string.text_limitTo)
                        .setNeutralButton(R.string.butn_none, noLimitListener)
                        .show();
            } else if (transferObject instanceof StorageStatusItem) {
                final StorageStatusItem statusItem = (StorageStatusItem) transferObject;

                if (statusItem.hasIssues(getAdapter()))
                    new AlertDialog.Builder(requireActivity())
                            .setMessage(getString(R.string.mesg_notEnoughSpace))
                            .setNegativeButton(R.string.butn_close, null)
                            .setPositiveButton(R.string.butn_saveTo, (dialog, which) -> changeSavePath(statusItem.directory))
                            .show();
                else
                    changeSavePath(statusItem.directory);
            } else if (transferObject instanceof TransferListAdapter.TransferFolder) {
                getAdapter().setPath(transferObject.directory);
                refreshList();
                AppUtils.showFolderSelectionHelp(this);
            } else
                return super.performLayoutClick(holder);

            return true;
        } catch (Exception ignored) {

        }

        return false;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);

        if (data != null && resultCode == Activity.RESULT_OK && requestCode == REQUEST_CHOOSE_FOLDER
                && data.hasExtra(FilePickerActivity.EXTRA_CHOSEN_PATH)) {
            final Uri selectedPath = data.getParcelableExtra(FilePickerActivity.EXTRA_CHOSEN_PATH);

            if (selectedPath == null) {
                createSnackbar(R.string.mesg_somethingWentWrong).show();
            } else if (selectedPath.toString().equals(getTransferGroup().savePath)) {
                createSnackbar(R.string.mesg_pathSameError).show();
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity())
                        .setTitle(R.string.ques_checkOldFiles)
                        .setMessage(R.string.text_checkOldFiles)
                        .setNeutralButton(R.string.butn_cancel, null)
                        .setNegativeButton(R.string.butn_skip, (dialogInterface, i) -> updateSavePath(selectedPath.toString()))
                        .setPositiveButton(R.string.butn_proceed, (dialogInterface, i) -> {
                                }
                                // FIXME: 21.03.2020
                        /*new BackgroundTask()
                        {
                            @Override
                            public void onRun()
                            {
                                TransferUtils.pauseTransfer(getContext(), mHeldGroup, TransferObject.Type.INCOMING);
                                List<TransferObject> checkList = AppUtils.getKuick(getService()).
                                        castQuery(new SQLQuery.Select(Kuick.TABLE_TRANSFER).setWhere(
                                                Kuick.FIELD_TRANSFER_GROUPID + "=? AND "
                                                        + Kuick.FIELD_TRANSFER_TYPE + "=?",
                                                String.valueOf(getTransferGroup().id),
                                                TransferObject.Type.INCOMING.toString()),
                                                TransferObject.class);

                                TransferGroup pseudoGroup = new TransferGroup(getTransferGroup().id);

                                try {
                                    // Illustrate new change to build the structure accordingly
                                    AppUtils.getKuick(getService()).reconstruct(pseudoGroup);
                                    pseudoGroup.savePath = selectedPath.toString();

                                    for (TransferObject transferObject : checkList) {
                                        if (isInterrupted())
                                            throw new InterruptedException();

                                        DocumentFile file;
                                        DocumentFile pseudoFile;

                                        publishStatusText(transferObject.name);

                                        try {
                                            file = FileUtils.getIncomingPseudoFile(getService(), transferObject,
                                                    getTransferGroup(), false);
                                            pseudoFile = FileUtils.getIncomingPseudoFile(getService(), transferObject,
                                                    pseudoGroup, true);
                                        } catch (Exception e) {
                                            continue;
                                        }

                                        if (file != null && pseudoFile != null) {
                                            if (file.canWrite())
                                                FileUtils.move(getService(), file, pseudoFile, this);
                                            else
                                                throw new IOException("Failed to access: " + file.getUri());
                                        }
                                    }

                                    updateSavePath(selectedPath.toString());
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }.setTitle(getString(R.string.mesg_organizingFiles))
                                .setIconRes(R.drawable.ic_compare_arrows_white_24dp_static)
                                .run(requireContext())*/);

                builder.show();
            }
        }
    }

    public TransferGroup getTransferGroup()
    {
        Bundle arguments = getArguments();

        if (mHeldGroup == null && arguments != null) {
            mHeldGroup = new TransferGroup(arguments.getLong(ARG_GROUP_ID, -1));

            try {
                AppUtils.getKuick(getContext()).reconstruct(mHeldGroup);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return mHeldGroup;
    }

    public void goPath(String path, long groupId, String deviceId, String type)
    {
        if (deviceId != null && type != null)
            try {
                ShowingAssignee assignee = new ShowingAssignee(groupId, deviceId, TransferObject.Type.valueOf(type));

                AppUtils.getKuick(getContext()).reconstruct(assignee);
                TransferUtils.loadAssigneeInfo(getContext(), assignee);

                getAdapter().setAssignee(assignee);
            } catch (Exception ignored) {
            }

        goPath(path, groupId);
    }

    public void goPath(String path, long groupId)
    {
        getAdapter().setGroupId(groupId);
        goPath(path);
    }

    public void goPath(String path)
    {
        getAdapter().setPath(path);
        refreshList();
    }

    public void updateSavePath(String selectedPath)
    {
        TransferGroup group = getTransferGroup();

        group.savePath = selectedPath;
        AppUtils.getKuick(getContext()).publish(group);
        AppUtils.getKuick(getContext()).broadcast();

        if (isAdded())
            requireActivity().runOnUiThread(() -> createSnackbar(R.string.mesg_pathSaved).show());
    }

    private static class SelectionCallback extends EditableListFragment.SelectionCallback
    {
        public SelectionCallback(android.app.Activity activity, PerformerEngineProvider provider)
        {
            super(activity, provider);
        }

        @Override
        public boolean onPerformerMenuList(PerformerMenu performerMenu, MenuInflater inflater, Menu targetMenu)
        {
            super.onPerformerMenuList(performerMenu, inflater, targetMenu);
            inflater.inflate(R.menu.action_mode_transfer, targetMenu);
            return true;
        }

        @Override
        public boolean onPerformerMenuSelected(PerformerMenu performerMenu, MenuItem item)
        {
            int id = item.getItemId();
            IPerformerEngine engine = getPerformerEngine();

            if (engine == null)
                return false;

            List<Selectable> genericList = new ArrayList<>(engine.getSelectionList());
            List<TransferListAdapter.GenericItem> selectionList = new ArrayList<>();

            for (Selectable selectable : genericList)
                if (selectable instanceof TransferListAdapter.GenericItem)
                    selectionList.add((TransferListAdapter.GenericItem) selectable);

            if (id == R.id.action_mode_transfer_delete) {
                DialogUtils.showRemoveTransferObjectListDialog(getActivity(), selectionList);
                return true;
            } else
                return super.onPerformerMenuSelected(performerMenu, item);
        }
    }
}
