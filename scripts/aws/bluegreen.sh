#!/usr/bin/env bash
# Blue/green deploy for linker1 on AWS (ALB with two target groups "a" and "b").
#
#   bluegreen.sh launch      create the new (green) VM, wait until it is healthy
#   bluegreen.sh open-test   open ALB test port 8081 for this runner's IP, pointed at green
#   bluegreen.sh close-test  close it again
#   bluegreen.sh ab          send 90% blue / 10% green, observe, check both healthy
#   bluegreen.sh promote     100% green, retire (terminate) the old blue VMs
#   bluegreen.sh rollback    100% back to blue, destroy green
#   bluegreen.sh urls        print the public URLs
#
# State lives in AWS itself (listener weights, instance tags), so no GitHub
# variables or Terraform state are needed between runs.
#
# Env: AWS_REGION (default us-east-1), REPO_URL, REPO_REF, LD_SDK_KEY (optional),
#      MYSQL_PWD (RDS password, optional), INSTANCE_TYPE (default t3.small), INSTANCE_PROFILE (optional),
#      OBSERVE_SECONDS (default 60), GITHUB_OUTPUT (optional, set by Actions).
set -euo pipefail

export AWS_DEFAULT_REGION="${AWS_REGION:-us-east-1}"
INSTANCE_TYPE="${INSTANCE_TYPE:-t3.small}"
OBSERVE_SECONDS="${OBSERVE_SECONDS:-60}"
STATE_FILE="${STATE_FILE:-.bluegreen-state}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

log() { echo "[bluegreen] $*" >&2; }
q()   { aws "$@" --output text; }

lookup() {
  ALB_ARN="$(q elbv2 describe-load-balancers --names linker1-alb --query 'LoadBalancers[0].LoadBalancerArn')"
  ALB_DNS="$(q elbv2 describe-load-balancers --names linker1-alb --query 'LoadBalancers[0].DNSName')"
  ALB_SG="$(q ec2 describe-security-groups --filters Name=group-name,Values=linker1-alb --query 'SecurityGroups[0].GroupId')"
  INST_SG="$(q ec2 describe-security-groups --filters Name=group-name,Values=linker1-instance --query 'SecurityGroups[0].GroupId')"
  TG_A="$(q elbv2 describe-target-groups --names linker1-a --query 'TargetGroups[0].TargetGroupArn')"
  TG_B="$(q elbv2 describe-target-groups --names linker1-b --query 'TargetGroups[0].TargetGroupArn')"
  L80="$(q elbv2 describe-listeners --load-balancer-arn "$ALB_ARN" --query 'Listeners[?Port==`80`].ListenerArn | [0]')"
  L8081="$(q elbv2 describe-listeners --load-balancer-arn "$ALB_ARN" --query 'Listeners[?Port==`8081`].ListenerArn | [0]')"
  for v in ALB_ARN ALB_SG INST_SG TG_A TG_B L80 L8081; do
    case "${!v}" in None|"") log "ERROR: $v not found. Did you run 'terraform apply' in infra-aws/ in this account/region?"; exit 1;; esac
  done
}

# Active (blue) = the target group holding >= 50% of the weight on port 80.
find_active() {
  ACTIVE_TG="$(q elbv2 describe-listeners --listener-arns "$L80" \
    --query 'Listeners[0].DefaultActions[0].ForwardConfig.TargetGroups[?Weight>=`50`].TargetGroupArn | [0]')"
  if [ "$ACTIVE_TG" = "$TG_A" ]; then IDLE_TG="$TG_B"; else ACTIVE_TG="$TG_B"; IDLE_TG="$TG_A"; fi
}

set_weights() { # <tg-for-100%> <tg-for-0%> [pct-for-first]
  local first="$1" second="$2" pct="${3:-100}"
  aws elbv2 modify-listener --listener-arn "$L80" --default-actions \
    "Type=forward,ForwardConfig={TargetGroups=[{TargetGroupArn=$first,Weight=$pct},{TargetGroupArn=$second,Weight=$((100 - pct))}]}" >/dev/null
}

healthy_count() { # <tg>
  q elbv2 describe-target-health --target-group-arn "$1" \
    --query 'length(TargetHealthDescriptions[?TargetHealth.State==`healthy`])'
}
registered_ids() { # <tg>
  q elbv2 describe-target-health --target-group-arn "$1" --query 'TargetHealthDescriptions[].Target.Id'
}

save_state() { printf '%s\n' "$@" > "$STATE_FILE"; }
load_state() {
  [ -f "$STATE_FILE" ] || { log "ERROR: $STATE_FILE missing (run 'launch' first)"; exit 1; }
  { read -r GREEN_ID; read -r IDLE_TG_SAVED; read -r ACTIVE_TG_SAVED; read -r FIRST_DEPLOY; } < "$STATE_FILE"
  IDLE_TG="$IDLE_TG_SAVED"; ACTIVE_TG="$ACTIVE_TG_SAVED"
}

render_user_data() {
  python3 - "$HERE/../../infra-aws/cloud-init.yaml" <<'PY'
import os, sys
tpl = open(sys.argv[1]).read()
def esc(v):  # systemd treats % and " specially inside Environment="..."
    return v.replace("%", "%%").replace('"', '\\"')
env = []
if os.environ.get("LD_SDK_KEY"):
    env.append('Environment="LD_SDK_KEY=%s"' % esc(os.environ["LD_SDK_KEY"]))
if os.environ.get("MYSQL_HOST"):
    for k in ("MYSQL_HOST", "MYSQL_DATABASE", "MYSQL_USER", "MYSQL_PWD"):
        env.append('Environment="%s=%s"' % (k, esc(os.environ[k])))
    env.append('Environment="LINKER_STORAGE=mysql"')
out = tpl.replace("__REPO_URL__", os.environ["REPO_URL"]).replace("__REPO_REF__", os.environ["REPO_REF"])
out = out.replace("__OPTIONAL_ENV__", "\n    ".join(env))
sys.stdout.write(out)
PY
}

cmd_launch() {
  : "${REPO_URL:?REPO_URL required}" "${REPO_REF:?REPO_REF required}"
  lookup; find_active
  local first_deploy=false
  [ -z "$(registered_ids "$ACTIVE_TG")" ] && first_deploy=true
  log "active=$ACTIVE_TG idle=$IDLE_TG first_deploy=$first_deploy"

  local ami subnet
  ami="$(q ec2 describe-images --owners 099720109477 \
    --filters 'Name=name,Values=ubuntu/images/hvm-ssd-gp3/ubuntu-noble-24.04-amd64-server-*' 'Name=state,Values=available' \
    --query 'sort_by(Images,&CreationDate)[-1].ImageId')"
  subnet="$(q ec2 describe-subnets --filters Name=default-for-az,Values=true --query 'Subnets[0].SubnetId')"
  log "ami=$ami subnet=$subnet"

  # Optional RDS: if the database exists and MYSQL_PWD is provided, links go to MySQL.
  export MYSQL_HOST="" MYSQL_DATABASE=linker MYSQL_USER=linker
  MYSQL_HOST="$(aws rds describe-db-instances --db-instance-identifier linker1-db \
    --query 'DBInstances[0].Endpoint.Address' --output text 2>/dev/null || true)"
  if [ "$MYSQL_HOST" = "None" ]; then MYSQL_HOST=""; fi
  if [ -n "$MYSQL_HOST" ] && [ -z "${MYSQL_PWD:-}" ]; then
    log "WARNING: RDS exists but MYSQL_PWD is not set; falling back to local SQLite (links will not persist)"
    MYSQL_HOST=""
  fi
  if [ -n "$MYSQL_HOST" ]; then log "storage: mysql ($MYSQL_HOST)"; else log "storage: sqlite (local to the VM)"; fi

  local ud; ud="$(mktemp)"; render_user_data > "$ud"
  local profile=(); [ -n "${INSTANCE_PROFILE:-}" ] && profile=(--iam-instance-profile "Name=$INSTANCE_PROFILE")
  GREEN_ID="$(q ec2 run-instances --image-id "$ami" --instance-type "$INSTANCE_TYPE" \
    --network-interfaces "DeviceIndex=0,SubnetId=$subnet,AssociatePublicIpAddress=true,Groups=$INST_SG" \
    --metadata-options HttpTokens=required \
    --block-device-mappings 'DeviceName=/dev/sda1,Ebs={VolumeSize=16,VolumeType=gp3}' \
    --user-data "file://$ud" "${profile[@]}" \
    --tag-specifications "ResourceType=instance,Tags=[{Key=Project,Value=linker1},{Key=Name,Value=linker1-${GITHUB_RUN_ID:-manual}},{Key=Ref,Value=${REPO_REF}}]" \
    --query 'Instances[0].InstanceId')"
  rm -f "$ud"
  log "launched $GREEN_ID"
  save_state "$GREEN_ID" "$IDLE_TG" "$ACTIVE_TG" "$first_deploy"
  [ -n "${GITHUB_OUTPUT:-}" ] && echo "green_id=$GREEN_ID" >> "$GITHUB_OUTPUT"

  aws ec2 wait instance-running --instance-ids "$GREEN_ID"
  aws elbv2 register-targets --target-group-arn "$IDLE_TG" --targets "Id=$GREEN_ID"

  # cloud-init clones the repo and builds with Maven before the app answers: allow 20 min.
  local i
  for i in $(seq 1 40); do
    if [ "$(healthy_count "$IDLE_TG")" -ge 1 ]; then log "green is healthy"; return 0; fi
    log "waiting for green to be healthy ($i/40)..."; sleep 30
  done
  log "ERROR: green never became healthy. Console output:"
  aws ec2 get-console-output --instance-id "$GREEN_ID" --latest --output text >&2 || true
  exit 1
}

cmd_open_test() {
  load_state; lookup
  local ip; ip="$(curl -fsS https://checkip.amazonaws.com | tr -d '[:space:]')"
  aws elbv2 modify-listener --listener-arn "$L8081" --default-actions "Type=forward,TargetGroupArn=$IDLE_TG" >/dev/null
  aws ec2 authorize-security-group-ingress --group-id "$ALB_SG" --protocol tcp --port 8081 --cidr "$ip/32" >/dev/null 2>&1 || true
  echo "$ip" > "$STATE_FILE.ip"
  [ -n "${GITHUB_OUTPUT:-}" ] && echo "test_url=http://$ALB_DNS:8081" >> "$GITHUB_OUTPUT"
  log "test URL http://$ALB_DNS:8081 (open for $ip/32)"
  sleep 15 # let the SG rule and listener change propagate
}

cmd_close_test() {
  lookup
  if [ -f "$STATE_FILE.ip" ]; then
    aws ec2 revoke-security-group-ingress --group-id "$ALB_SG" --protocol tcp --port 8081 --cidr "$(cat "$STATE_FILE.ip")/32" >/dev/null 2>&1 || true
    rm -f "$STATE_FILE.ip"
  fi
}

cmd_ab() {
  load_state; lookup
  if [ "$FIRST_DEPLOY" = "true" ]; then log "first deploy: nothing to compare against, skipping A/B"; return 0; fi
  set_weights "$ACTIVE_TG" "$IDLE_TG" 90
  log "A/B: 90% blue / 10% green for ${OBSERVE_SECONDS}s"
  sleep "$OBSERVE_SECONDS"
  [ "$(healthy_count "$ACTIVE_TG")" -ge 1 ] && [ "$(healthy_count "$IDLE_TG")" -ge 1 ] \
    || { log "ERROR: a backend is unhealthy after the A/B window"; exit 1; }
}

cmd_promote() {
  load_state; lookup
  set_weights "$IDLE_TG" "$ACTIVE_TG" 100
  log "promoted: green now serves 100%"
  local old; old="$(registered_ids "$ACTIVE_TG" || true)"
  if [ -n "$old" ] && [ "$old" != "None" ]; then
    for id in $old; do aws elbv2 deregister-targets --target-group-arn "$ACTIVE_TG" --targets "Id=$id" >/dev/null; done
    # Only ever terminate instances this project created.
    for id in $old; do
      if [ "$(q ec2 describe-tags --filters "Name=resource-id,Values=$id" Name=key,Values=Project --query 'Tags[0].Value')" = "linker1" ]; then
        log "terminating old blue VM $id"; aws ec2 terminate-instances --instance-ids "$id" >/dev/null
      fi
    done
  fi
}

cmd_rollback() {
  [ -f "$STATE_FILE" ] || { log "nothing to roll back"; return 0; }
  load_state; lookup
  [ "$FIRST_DEPLOY" = "true" ] || set_weights "$ACTIVE_TG" "$IDLE_TG" 100
  aws elbv2 deregister-targets --target-group-arn "$IDLE_TG" --targets "Id=$GREEN_ID" >/dev/null 2>&1 || true
  aws ec2 terminate-instances --instance-ids "$GREEN_ID" >/dev/null 2>&1 || true
  log "rolled back; green $GREEN_ID destroyed"
}

cmd_urls() {
  lookup
  echo "ALB:   http://$ALB_DNS"
  local cf; cf="$(q cloudfront list-distributions --query "DistributionList.Items[?Comment=='linker1'].DomainName | [0]" 2>/dev/null || true)"
  [ -n "$cf" ] && [ "$cf" != "None" ] && echo "HTTPS: https://$cf"
  return 0
}

case "${1:-}" in
  launch) cmd_launch;; open-test) cmd_open_test;; close-test) cmd_close_test;;
  ab) cmd_ab;; promote) cmd_promote;; rollback) cmd_rollback;; urls) cmd_urls;;
  *) echo "usage: $0 {launch|open-test|close-test|ab|promote|rollback|urls}" >&2; exit 2;;
esac
