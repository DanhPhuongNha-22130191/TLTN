<?php

$verified_tls = [
    'ssl' => [
        'verify_peer' => true,
        'verify_peer_name' => true,
        'allow_self_signed' => false,
        'cafile' => '/run/secrets/tls/mail-ca.pem',
    ],
];

$config['imap_conn_options'] = $verified_tls;
$config['smtp_conn_options'] = $verified_tls;
$config['product_name'] = 'GitLab Handbook Mail';
