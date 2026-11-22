package dev.qumara.entity;

public enum QumaraState { SPAWN, IDLE, ATTACK, STUN, FURY, DEAD;
    public static QumaraState of(int i) { QumaraState[] v = values(); return v[Math.max(0, Math.min(v.length - 1, i))]; }
}
