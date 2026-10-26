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
class GatekeeperAction {
  const GatekeeperAction({required this.type, required this.signature});

  factory GatekeeperAction.fromMap(Map<Object?, Object?> map) => GatekeeperAction(
        type: ActionType.fromWire(map['type']! as String),
        signature: map['signature']! as String,
      );

  final ActionType type;
  final String signature;

  @override
  String toString() => 'GatekeeperAction(type: ${type.name}, signature: $signature)';
}

/// Error codes shared by the iOS and Android implementations.
enum GatekeeperErrorCode {
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
  unknownError('UNKNOWN_ERROR');

  const GatekeeperErrorCode(this.wireValue);

  /// The value used on the platform channel.
  final String wireValue;

  static GatekeeperErrorCode fromWire(String value) => values.firstWhere(
        (code) => code.wireValue == value,
        orElse: () => GatekeeperErrorCode.unknownError,
      );
}

/// Thrown (or emitted on the subscribe stream) when the SDK reports a failure.
class GatekeeperException implements Exception {
  const GatekeeperException(this.code, {this.message, this.details});

  final GatekeeperErrorCode code;
  final String? message;

  /// Optional extra information sent by the native SDK.
  final Object? details;

  @override
  String toString() => 'GatekeeperException(${code.wireValue}${message != null ? ': $message' : ''})';
}
