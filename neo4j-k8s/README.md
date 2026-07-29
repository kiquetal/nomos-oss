# Neo4j on Kubernetes (Helm)

## Prerequisites

- Kubernetes cluster (minikube, EKS, etc.)
- Helm 3.x installed
- `kubectl` configured

## Installation

### 1. Add the Neo4j Helm repo

```bash
helm repo add neo4j https://helm.neo4j.com/neo4j
helm repo update
```

### 2. Install Neo4j (Community Edition, single instance)

```bash
helm install neo4j neo4j/neo4j \
  --namespace nomos \
  --create-namespace \
  --set neo4j.name=neo4j \
  --set neo4j.edition=community \
  --set neo4j.password=nomos-secret \
  --set neo4j.resources.requests.memory=1Gi \
  --set neo4j.resources.requests.cpu=500m \
  --set neo4j.resources.limits.memory=2Gi \
  --set neo4j.resources.limits.cpu=1000m \
  --set volumes.data.mode=defaultStorageClass \
  --set volumes.data.defaultStorageClass.requests.storage=10Gi
```

### 3. Verify

```bash
kubectl get pods -n nomos
# neo4j-0   1/1   Running

kubectl get svc -n nomos
# neo4j   ClusterIP   bolt://7687, http://7474
```

### 4. Access Neo4j Browser (port-forward for local testing)

```bash
kubectl port-forward svc/neo4j -n nomos 7474:7474 7687:7687
```

Then open http://localhost:7474 — login with `neo4j` / `nomos-secret`.

## Connection from Nomos (Quarkus)

In `application.properties`:

```properties
quarkus.neo4j.uri=bolt://neo4j.nomos.svc.cluster.local:7687
quarkus.neo4j.authentication.username=neo4j
quarkus.neo4j.authentication.password=nomos-secret
```

## Seed the graph

Once Nomos is running, either:

1. **Via Neo4j Browser** — paste `seed-data.cypher` contents
2. **Via Admin API** — run `http-requests/01-setup.http`
3. **Via cypher-shell**:
   ```bash
   kubectl exec -it neo4j-0 -n nomos -- cypher-shell -u neo4j -p nomos-secret < seed-data.cypher
   ```

## Backup (CronJob to S3)

```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: neo4j-backup
  namespace: nomos
spec:
  schedule: "0 3 * * *"
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: backup
            image: neo4j:5
            command:
            - /bin/sh
            - -c
            - |
              neo4j-admin database dump neo4j --to-path=/backup &&
              aws s3 cp /backup/neo4j.dump s3://your-bucket/neo4j/$(date +%Y%m%d).dump
            volumeMounts:
            - name: data
              mountPath: /data
            - name: backup
              mountPath: /backup
          volumes:
          - name: data
            persistentVolumeClaim:
              claimName: data-neo4j-0
          - name: backup
            emptyDir: {}
          restartPolicy: OnFailure
```

## Uninstall

```bash
helm uninstall neo4j -n nomos
kubectl delete pvc data-neo4j-0 -n nomos  # removes data!
```
