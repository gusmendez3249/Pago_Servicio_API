import '../../../../core/http/api_client.dart';
import '../../../../core/http/result.dart';
import '../models/login_request_model.dart';
import '../models/login_response_model.dart';

class AuthRemoteDataSource {
  const AuthRemoteDataSource(this._api);

  final ApiClient _api;

  Future<Result<LoginResponseModel>> login(LoginRequestModel peticion) {
    return _api.post<LoginResponseModel>(
      '/api/v1/auth/login',
      body: peticion.toJson(),
      autenticada: false,
      parser: (data) =>
          LoginResponseModel.fromJson(Map<String, dynamic>.from(data as Map)),
    );
  }

  Future<Result<void>> logout() {
    return _api.post<void>('/api/v1/auth/logout', parser: (_) {});
  }
}
