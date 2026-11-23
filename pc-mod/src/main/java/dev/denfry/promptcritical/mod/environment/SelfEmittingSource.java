package dev.denfry.promptcritical.mod.environment;

/**
 * Блок сам регистрирует свой источник с учётом своей защиты ({@link RadiationService#source}) — сканер контейнеров
 * ({@link ContainerActivityScan}) его пропускает, иначе предметы внутри посчитались бы второй раз и без экрана.
 */
public interface SelfEmittingSource {
}
