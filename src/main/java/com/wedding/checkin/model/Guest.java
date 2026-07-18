package com.wedding.checkin.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.time.Instant;

@Entity
public class Guest {

    @Id
    private String id;

    private String nome;

    private String tavolo;

    private boolean checkedIn = false;

    private Instant checkedInAt;

    public Guest() {
    }

    public Guest(String id, String nome, String tavolo) {
        this.id = id;
        this.nome = nome;
        this.tavolo = tavolo;
        this.checkedIn = false;
        this.checkedInAt = null;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getTavolo() {
        return tavolo;
    }

    public void setTavolo(String tavolo) {
        this.tavolo = tavolo;
    }

    public boolean isCheckedIn() {
        return checkedIn;
    }

    public void setCheckedIn(boolean checkedIn) {
        this.checkedIn = checkedIn;
    }

    public Instant getCheckedInAt() {
        return checkedInAt;
    }

    public void setCheckedInAt(Instant checkedInAt) {
        this.checkedInAt = checkedInAt;
    }
}
