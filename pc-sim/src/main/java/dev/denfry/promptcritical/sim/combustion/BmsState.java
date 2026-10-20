package dev.denfry.promptcritical.sim.combustion;

/** Состояния BMS по NFPA 85 (07 §7.9.6). */
public enum BmsState { OFF, PURGE, PURGE_COMPLETE, IGNITER_TRIAL, IGNITER_ON, MAIN_TRIAL, RUNNING, MFT, POST_PURGE }
