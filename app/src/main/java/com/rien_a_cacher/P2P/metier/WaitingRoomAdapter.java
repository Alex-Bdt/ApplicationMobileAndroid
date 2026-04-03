package com.rien_a_cacher.P2P.metier;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.rien_a_cacher.R;

import java.io.File;
import java.util.List;

public class WaitingRoomAdapter extends RecyclerView.Adapter<WaitingRoomAdapter.PlayerViewHolder> {

    private final List<PlayerInfo> players;

    public WaitingRoomAdapter(List<PlayerInfo> players) {
        this.players = players;
    }

    public static class PlayerViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvName;
        TextView  tvHost;

        public PlayerViewHolder(View view) {
            super(view);
            ivAvatar = view.findViewById(R.id.ivAvatar);
            tvName   = view.findViewById(R.id.tvPlayerName);
            tvHost   = view.findViewById(R.id.tvHostBadge);
        }
    }

    @Override
    public PlayerViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_waiting_player, parent, false);
        return new PlayerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(PlayerViewHolder holder, int position) {
        PlayerInfo player = players.get(position);

        holder.tvName.setText(player.name);
        holder.tvHost.setVisibility(player.isHost ? View.VISIBLE : View.GONE);

        if (player.photoPath != null && !player.photoPath.isEmpty()) {
            Glide.with(holder.ivAvatar.getContext())
                    .load(new File(player.photoPath))
                    .circleCrop()
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .signature(new com.bumptech.glide.signature.ObjectKey(
                            String.valueOf(System.currentTimeMillis())))
                    .placeholder(R.drawable.ic_profile_placeholder)
                    .into(holder.ivAvatar);
        } else {
            holder.ivAvatar.setImageResource(R.drawable.ic_profile_placeholder);
        }
    }

    @Override
    public int getItemCount() {
        return players.size();
    }
}
