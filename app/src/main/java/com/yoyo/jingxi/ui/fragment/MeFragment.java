package com.yoyo.jingxi.ui.fragment;
import com.yoyo.jingxi.ui.activity.AddPersonaActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.yoyo.jingxi.ui.activity.AddPersonaActivity;
import com.yoyo.jingxi.R;
import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.MyPersona;
import com.yoyo.jingxi.ui.adapter.MyPersonaAdapter;

public class MeFragment extends Fragment {

    private RecyclerView rvMyPersonas;
    private MyPersonaAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_me, container, false);

        rvMyPersonas = view.findViewById(R.id.rvMyPersonas);
        rvMyPersonas.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new MyPersonaAdapter(new MyPersonaAdapter.OnPersonaClickListener() {
            @Override
            public void onEditClick(MyPersona persona) {
                Intent intent = new Intent(getContext(), AddPersonaActivity.class);
                intent.putExtra("persona_name", persona.name);
                startActivity(intent);
            }

            @Override
            public void onSetMainClick(MyPersona persona) {
                new Thread(() -> {
                    AppDatabase db = AppDatabase.getDatabase(getContext());
                    db.myPersonaDao().clearAllMainStatus();
                    persona.isMainPersona = true;
                    db.myPersonaDao().update(persona);
                }).start();
            }

            @Override
            public void onDeleteClick(MyPersona persona) {
                if (persona != null) {
                    new Thread(() -> {
                        AppDatabase.getDatabase(getContext()).myPersonaDao().delete(persona);
                    }).start();
                }
            }
        });
        rvMyPersonas.setAdapter(adapter);

        FloatingActionButton fabAddPersona = view.findViewById(R.id.fabAddPersona);
        fabAddPersona.setOnClickListener(v -> {
            startActivity(new Intent(getContext(), AddPersonaActivity.class));
        });

        AppDatabase.getDatabase(getContext()).myPersonaDao().getAllMyPersonas().observe(getViewLifecycleOwner(), personas -> {
            adapter.setPersonas(personas);
        });

        return view;
    }
}
