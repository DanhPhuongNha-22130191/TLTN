<?php

$config['password_driver'] = 'httpapi';
$config['password_confirm_current'] = true;
$config['password_minimum_length'] = 8;
$config['password_username_format'] = '%u';
$config['password_httpapi_url'] = 'http://mail-account-manager:8080/password';
$config['password_httpapi_method'] = 'POST';
$config['password_httpapi_var_user'] = 'username';
$config['password_httpapi_var_curpass'] = 'currentPassword';
$config['password_httpapi_var_newpass'] = 'newPassword';
