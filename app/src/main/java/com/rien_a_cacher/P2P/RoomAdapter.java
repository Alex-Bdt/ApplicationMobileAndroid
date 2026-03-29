package com.rien_a_cacher.P2P;

import android.net.wifi.p2p.WifiP2pDevice;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.rien_a_cacher.R;

import java.util.List;

public class RoomAdapter extends RecyclerView.Adapter<RoomAdapter.RoomViewHolder> {

    public interface OnRoomClickListener {
        void onRoomClick(WifiP2pDevice device);
    }

    private final List<WifiP2pDevice> devices;
    private final OnRoomClickListener listener;

    public RoomAdapter(List<WifiP2pDevice> devices, OnRoomClickListener listener) {
        this.devices = devices;
        this.listener = listener;
    }

    public static class RoomViewHolder extends RecyclerView.ViewHolder {
        TextView tvRoomName;
        TextView tvRoomAddress;

        public RoomViewHolder(View view) {
            super(view);
            tvRoomName    = view.findViewById(R.id.tvRoomName);
            tvRoomAddress = view.findViewById(R.id.tvRoomAddress);
        }
    }

    @Override
    public RoomViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_room, parent, false);
        return new RoomViewHolder(view);
    }

    @Override
    public void onBindViewHolder(RoomViewHolder holder, int position) {
        WifiP2pDevice device = devices.get(position);

        // Affiche le nom de l'appareil ou l'adresse si nom indisponible
        String name = (device.deviceName != null && !device.deviceName.isEmpty())
                ? device.deviceName : "Salle inconnue";
        holder.tvRoomName.setText(name);
        holder.tvRoomAddress.setText(device.deviceAddress);

        holder.itemView.setOnClickListener(v -> listener.onRoomClick(device));
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }
}
