package dev.denfry.promptcritical.sim.mining;

/**
 * Рудное тело ячейки (08 §8.1.1) — линза с полуосями a = b, c и наклоном, либо угольная свита (§8.1.4): 1–3
 * пласта мощностью {@code thickness} с шагом {@code spacing}, падение до 10°, эллиптический контур с полуосями
 * {@code rx}, {@code rz} по простиранию и вкрест. Неизменяемое; строится детерминированно из сида и ячейки
 * ({@link VeinField#body}).
 */
public final class VeinBody {
    final VeinType type;
    final int cellX, cellZ;
    /** Центр тела; у свиты y0 — кровля верхнего пласта в центре. */
    final double x0, y0, z0;
    /** Линза: a (= b), c. */
    final double a, c;
    /** Свита: полуоси контура. */
    final double rx, rz;
    /** Азимут (по простиранию) и наклон: cos/sin. Для свиты наклон хранится тангенсом. */
    final double cosAz, sinAz, cosTilt, sinTilt, tanDip;
    final int[] thickness, spacing;
    final int salt;
    /** Описывающий параллелепипед, блоки (включительно). */
    final int minX, maxX, minY, maxY, minZ, maxZ;

    VeinBody(VeinType type, int cellX, int cellZ, double x0, double y0, double z0, double a, double c, double rx,
            double rz, double azimuth, double tilt, int[] thickness, int[] spacing, int salt) {
        this.type = type;
        this.cellX = cellX;
        this.cellZ = cellZ;
        this.x0 = x0;
        this.y0 = y0;
        this.z0 = z0;
        this.a = a;
        this.c = c;
        this.rx = rx;
        this.rz = rz;
        this.cosAz = StrictMath.cos(azimuth);
        this.sinAz = StrictMath.sin(azimuth);
        this.cosTilt = StrictMath.cos(tilt);
        this.sinTilt = StrictMath.sin(tilt);
        this.tanDip = StrictMath.tan(tilt);
        this.thickness = thickness;
        this.spacing = spacing;
        this.salt = salt;
        double horiz, up, down;
        if (type.seam()) {
            horiz = Math.max(rx, rz);
            up = horiz * tanDip + 1;
            int depth = 0;
            for (int k = 0; k < thickness.length; k++) depth += (k > 0 ? spacing[k - 1] : 0);
            down = up + depth + thickness[thickness.length - 1] + 2;
        } else {
            horiz = a + c;
            up = down = Math.hypot(a * sinTilt, c * cosTilt) + 1;
        }
        minX = (int) Math.floor(x0 - horiz);
        maxX = (int) Math.ceil(x0 + horiz);
        minZ = (int) Math.floor(z0 - horiz);
        maxZ = (int) Math.ceil(z0 + horiz);
        minY = (int) Math.floor(y0 - down);
        maxY = (int) Math.ceil(y0 + up);
    }

    public VeinType type() {
        return type;
    }

    public int cellX() {
        return cellX;
    }

    public int cellZ() {
        return cellZ;
    }

    public double centerX() {
        return x0;
    }

    public double centerY() {
        return y0;
    }

    public double centerZ() {
        return z0;
    }

    /** Полуось линзы a (= b); у свиты — NaN. */
    public double semiAxisA() {
        return type.seam() ? Double.NaN : a;
    }

    /** Полуось линзы c; у свиты — NaN. */
    public double semiAxisC() {
        return type.seam() ? Double.NaN : c;
    }

    /** Число пластов свиты; у линзы 0. */
    public int seamCount() {
        return type.seam() ? thickness.length : 0;
    }

    /** Мощность k-го пласта, блоков. */
    public int seamThickness(int k) {
        return thickness[k];
    }

    /** Шаг между кровлями k-го и (k+1)-го пласта, блоков. */
    public int seamSpacing(int k) {
        return spacing[k];
    }

    public int minX() {
        return minX;
    }

    public int maxX() {
        return maxX;
    }

    public int minY() {
        return minY;
    }

    public int maxY() {
        return maxY;
    }

    public int minZ() {
        return minZ;
    }

    public int maxZ() {
        return maxZ;
    }

    boolean intersects(int x0b, int x1b, int z0b, int z1b) {
        return maxX >= x0b && minX <= x1b && maxZ >= z0b && minZ <= z1b;
    }

    boolean inBox(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }
}
