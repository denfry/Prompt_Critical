package dev.denfry.promptcritical.sim.automation;

/** Union-find по цепям шкафов A1 (03d §3d.3.2): сжатие путей и объединение по рангу, без аллокаций в работе. */
final class ContactGraph {
    private int[] parent = new int[0];
    private byte[] rank = new byte[0];

    void ensure(int nodes) {
        if (parent.length >= nodes) return;
        int n = Math.max(nodes, parent.length * 2);
        int[] p = new int[n];
        byte[] r = new byte[n];
        System.arraycopy(parent, 0, p, 0, parent.length);
        System.arraycopy(rank, 0, r, 0, rank.length);
        parent = p;
        rank = r;
    }

    void reset(int node) {
        parent[node] = node;
        rank[node] = 0;
    }

    int find(int x) {
        int r = x;
        while (parent[r] != r) r = parent[r];
        while (parent[x] != r) {
            int next = parent[x];
            parent[x] = r;
            x = next;
        }
        return r;
    }

    void union(int a, int b) {
        int ra = find(a), rb = find(b);
        if (ra == rb) return;
        if (rank[ra] < rank[rb]) {
            parent[ra] = rb;
        } else {
            parent[rb] = ra;
            if (rank[ra] == rank[rb]) rank[ra]++;
        }
    }
}
