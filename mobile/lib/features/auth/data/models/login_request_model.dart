class LoginRequestModel {
  const LoginRequestModel({required this.username, required this.password});

  final String username;
  final String password;

  Map<String, dynamic> toJson() => {
        'bandera': 1,
        'username': username,
        'password': password,
      };
}
