<?php

$insecure_tls = [
    'ssl' => [
        'verify_peer' => false,
        'verify_peer_name' => false,
        'allow_self_signed' => true,
    ],
];

$config['imap_conn_options'] = $insecure_tls;
$config['smtp_conn_options'] = $insecure_tls;
$config['product_name'] = 'GitLab Handbook Mail';
