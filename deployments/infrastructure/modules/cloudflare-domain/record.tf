resource "cloudflare_dns_record" "cluster_wildcard" {
  name    = "*.${var.domain_name}"
  ttl     = 1
  type    = "A"
  zone_id = var.cloudflare_zone_id
  proxied = true
  content = var.ip_address
}
