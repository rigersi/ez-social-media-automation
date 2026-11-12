set -e
ZONE=us-west1-b
NAME=ez-factory

if ! gcloud compute instances describe "$NAME" --zone="$ZONE" >/dev/null 2>&1; then
  gcloud compute instances create "$NAME" --zone="$ZONE" --machine-type=e2-micro \
    --image-family=debian-12 --image-project=debian-cloud --boot-disk-size=30GB \
    --tags=ez-postiz
fi
gcloud compute firewall-rules create ez-postiz-ui --allow=tcp:4007 --target-tags=ez-postiz 2>/dev/null || true

cat > /tmp/ez-install.sh <<'EOF'
set -e
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq openjdk-17-jdk-headless ffmpeg git docker.io docker-compose-v2 >/dev/null
if [ ! -f /swapfile ]; then
  fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
  grep -q swapfile /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi
systemctl enable --now docker
mkdir -p /opt/ez && cd /opt/ez
rm -rf ez-social-media-automation
git clone --depth 1 "https:""//github.com/rigersi/ez-social-media-automation.git"
cd ez-social-media-automation
mkdir -p topics/custom config data output
[ -f topics/fresh-2026.json ] && mv -f topics/fresh-2026.json topics/custom/ || true
bash build.sh
mkdir -p /opt/postiz && cd /opt/postiz
cat > compose.yaml <<'YAML'
services:
  postiz:
    image: ghcr.io/gitroomhq/postiz-app:latest
    restart: always
    ports: ["4007:5000"]
    environment:
      MAIN_URL: http://localhost:4007
      FRONTEND_URL: http://localhost:4007
      POSTIZ_DB_NAME: postiz
      POSTIZ_DB_USER: postiz
      POSTIZ_DB_PASSWORD: ezpostizpw
      POSTIZ_DB_HOST: db
      JWT_SECRET: ezjwtsecret-change-me-superlong
      API_KEY_SECRET: ezapisecret-change-me-superlong
    depends_on: [db]
  db:
    image: postgres:17-alpine
    restart: always
    environment:
      POSTGRES_PASSWORD: ezpostizpw
      POSTGRES_USER: postiz
      POSTGRES_DB: postiz
    volumes: ["pgdata:/var/lib/postgresql/data"]
volumes: { pgdata: {} }
YAML
docker compose up -d
cd /opt/ez/ez-social-media-automation
cat > config/config.json <<'JSON'
{
  "brand": { "name": "EZ Good Finds", "logoPath": "brand/logo.png", "logoMargin": 36, "logoMaxWidth": 190 },
  "canvas": { "width": 1080, "height": 1920 },
  "slots": { "timezone": "Europe/Tirane", "times": ["10:00", "18:00"], "leadMinutes": 90, "pollSeconds": 30 },
  "llm": { "provider": "none" },
  "comfyui": { "mode": "fallback" },
  "art": { "provider": "auto", "gemini": { "apiKey": "PASTE_GEMINI_KEY_HERE", "model": "gemini-3.1-flash-image", "monthlyCapImages": 60 } },
  "video": { "enabled": true, "ffmpegPath": "ffmpeg", "seconds": 10, "fps": 30 },
  "postiz": { "baseUrl": "http://localhost:4007/api/public/v1", "apiKey": "PASTE_POSTIZ_KEY_HERE", "channels": ["x", "youtube", "tiktok", "instagram", "facebook", "threads"], "youtubePlaylistId": "" },
  "publish": { "mode": "dry-run", "outbox": "output/publish" },
  "feedback": { "enabled": false, "port": 8787 },
  "hashtags": { "poolPath": "trends/hashtag-pool.json", "min": 12, "max": 28 },
  "dataDir": "data", "outDir": "output", "topicsDir": "topics"
}
JSON
cat > /etc/systemd/system/ez-factory.service <<'SVC'
[Unit]
Description=EZ Content Factory
After=network.target docker.service
[Service]
WorkingDirectory=/opt/ez/ez-social-media-automation
ExecStart=/usr/bin/java -cp target/classes ez.factory.Main daemon --config config/config.json
Restart=always
[Install]
WantedBy=multi-user.target
SVC
systemctl daemon-reload
echo "ALWAYS-ON READY - VM IP: $(curl -s http://metadata.google.internal/computeMetadata/v1/instance/network-interfaces/0/access-configs/0/external-ip -H 'Metadata-Flavor: Google')"
EOF

gcloud compute scp /tmp/ez-install.sh "$NAME":/tmp/ --zone="$ZONE"
gcloud compute ssh "$NAME" --zone="$ZONE" --command="sudo bash /tmp/ez-install.sh"