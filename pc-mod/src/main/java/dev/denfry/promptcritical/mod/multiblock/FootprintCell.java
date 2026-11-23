package dev.denfry.promptcritical.mod.multiblock;

/**
 * Клетка многоклеточной конструкции, которая не якорь: невидимая {@link MultiblockPartBlock} или особая клетка со
 * своим поведением (встроенный путь клети шахты). Состояние несёт {@link MultiblockPartBlock#CELL}; конструкция
 * ставит, переносит и разбирает такие клетки вместе с собой.
 */
public interface FootprintCell {}
