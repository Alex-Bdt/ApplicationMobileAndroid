package com.rien_a_cacher;

import java.util.List;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

public class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder> {

    private final List<GamePhoto> photos;
    public PhotoAdapter(List<GamePhoto> photos) {
        this.photos = photos;
    }

    public static class PhotoViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;

        public PhotoViewHolder(View view) {
            super(view);
            imageView = view.findViewById(R.id.imageView);
        }
    }

    @Override
    public PhotoViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_photo, parent, false);
        return new PhotoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(PhotoViewHolder holder, int position) {
        GamePhoto photo = photos.get(position);

        // Glide charge depuis le fichier copié (plus stable que l'URI pour l'affichage)
        Glide.with(holder.imageView.getContext())
                .load(photo.getFilePath())
                .diskCacheStrategy(DiskCacheStrategy.NONE)  // ← ignore le cache disque
                .skipMemoryCache(true)                       // ← ignore le cache mémoire
                .centerCrop()
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_close_clear_cancel)
                .into(holder.imageView);
    }

    @Override
    public int getItemCount() {
        return photos.size();
    }
}
