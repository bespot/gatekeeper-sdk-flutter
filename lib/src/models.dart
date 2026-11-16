/// The action returned by Gatekeeper for the current device state.
enum ActionType {
  block('BLOCK'),
  limitAccess('LIMIT_ACCESS'),
  monitor('MONITOR'),
  notSafe('NOT_SAFE'),
  safe('SAFE'),
  unknown('DEFAULT_UNKNOWN_ACTION');

  const ActionType(this.wireValue);

  /// The value used on the platform channel.
  final String wireValue;

  static ActionType fromWire(String value) => values.firstWhere(
        (type) => type.wireValue == value,
        orElse: () => ActionType.unknown,
      );
}

/// A Gatekeeper result: the action to take and its server-verifiable signature.
class SafeSDKAction {
  const SafeSDKAction({required this.type, required this.signature});

  factory SafeSDKAction.fromMap(Map<Object?, Object?> map) => SafeSDKAction(
        type: ActionType.fromWire(map['type']! as String),
        signature: map['signature']! as String,
      );

  final ActionType type;
  final String signature;

  @override
  String toString() => 'SafeSDKAction(type: ${type.name}, signature: $signature)';
}

/// Error codes shared by the iOS and Android implementations.
enum SafeSDKErrorCode {
  networkConnection('NETWORK_CONNECTION'),
  noActiveApiKey('NO_ACTIVE_API_KEY'),
  quotaReachedForPlan('QUOTA_REACHED_FOR_PLAN'),
  noChecksAvailable('NO_CHECKS_AVAILABLE'),
  noRecipeFound('NO_RECIPE_FOUND'),
  notInitialized('NOT_INITIALIZED'),
  serverError('SERVER_ERROR'),
  invalidToken('INVALID_TOKEN'),
  authError('AUTH_ERROR'),
  alreadyInitialized('ALREADY_INITIALIZED'),
  invalidArguments('INVALID_ARGUMENTS'),
  unimplemented('UNIMPLEMENTED'),
  unknownError('UNKNOWN_ERROR');

  const SafeSDKErrorCode(this.wireValue);

  /// The value used on the platform channel.
  final String wireValue;

  static SafeSDKErrorCode fromWire(String value) => values.firstWhere(
        (code) => code.wireValue == value,
        orElse: () => SafeSDKErrorCode.unknownError,
      );
}

/// Thrown (or emitted on the subscribe stream) when the SDK reports a failure.
class SafeSDKException implements Exception {
  const SafeSDKException(this.code, {this.message, this.details});

  final SafeSDKErrorCode code;
  final String? message;

  /// Optional extra information sent by the native SDK.
  final Object? details;

  @override
  String toString() => 'SafeSDKException(${code.wireValue}${message != null ? ': $message' : ''})';
}
