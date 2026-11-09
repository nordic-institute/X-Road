#!/usr/bin/env python3
"""
YAML helper script for X-Road configuration management.
Provides simple operations to read and write YAML configuration values.
"""

import sys
import yaml
from pathlib import Path


def parse_path(path_str):
    """
    Parse a dot-notation path string into a list of keys.
    Handles quoted keys like: .xroad.proxy.tls."certificate-provisioning"."common-name"
    
    Args:
        path_str: Path string starting with optional '.'
        
    Returns:
        List of keys
    """
    if path_str.startswith('.'):
        path_str = path_str[1:]
    
    keys = []
    current_key = []
    in_quotes = False
    quote_char = None
    
    for char in path_str:
        if char in ('"', "'") and not in_quotes:
            in_quotes = True
            quote_char = char
        elif char == quote_char and in_quotes:
            in_quotes = False
            quote_char = None
        elif char == '.' and not in_quotes:
            if current_key:
                keys.append(''.join(current_key))
                current_key = []
        else:
            current_key.append(char)
    
    if current_key:
        keys.append(''.join(current_key))
    
    return keys


def get_value(data, keys):
    """
    Get a value from nested dict using a list of keys.
    Returns None if path doesn't exist.
    """
    current = data
    for key in keys:
        if not isinstance(current, dict) or key not in current:
            return None
        current = current[key]
    return current


def set_value(data, keys, value):
    """
    Set a value in nested dict using a list of keys.
    Creates intermediate dicts as needed.
    """
    current = data
    for key in keys[:-1]:
        if key not in current:
            current[key] = {}
        elif not isinstance(current[key], dict):
            # Overwrite non-dict values with dict
            current[key] = {}
        current = current[key]
    
    # Set the final value
    current[keys[-1]] = value


def load_yaml(file_path):
    """Load YAML file, returns empty dict if file doesn't exist or is empty."""
    path = Path(file_path)
    if not path.exists() or path.stat().st_size == 0:
        return {}
    
    with open(file_path, 'r') as f:
        content = yaml.safe_load(f)
        return content if content is not None else {}


def save_yaml(file_path, data):
    """Save data to YAML file."""
    # Ensure parent directory exists
    Path(file_path).parent.mkdir(parents=True, exist_ok=True)
    
    with open(file_path, 'w') as f:
        yaml.dump(data, f, default_flow_style=False, sort_keys=False, allow_unicode=True)


def cmd_get(args):
    """Get a value from YAML file."""
    if len(args) < 2:
        print("Usage: yaml_helper.py get <file> <path>", file=sys.stderr)
        sys.exit(1)
    
    file_path = args[0]
    path = args[1]
    
    data = load_yaml(file_path)
    keys = parse_path(path)
    value = get_value(data, keys)
    
    if value is None:
        # Return empty string for missing values (compatible with yq behavior)
        print("")
        sys.exit(0)
    else:
        print(value)
        sys.exit(0)


def cmd_exists(args):
    """Check if a path exists in YAML file."""
    if len(args) < 2:
        print("Usage: yaml_helper.py exists <file> <path>", file=sys.stderr)
        sys.exit(1)
    
    file_path = args[0]
    path = args[1]
    
    data = load_yaml(file_path)
    keys = parse_path(path)
    value = get_value(data, keys)
    
    # Exit code 0 if exists and not null, 1 otherwise (compatible with yq -e behavior)
    if value is not None and value != "null":
        sys.exit(0)
    else:
        sys.exit(1)


def cmd_set(args):
    """Set a value in YAML file."""
    if len(args) < 3:
        print("Usage: yaml_helper.py set <file> <path> <value>", file=sys.stderr)
        sys.exit(1)
    
    file_path = args[0]
    path = args[1]
    value = args[2]
    
    data = load_yaml(file_path)
    keys = parse_path(path)
    set_value(data, keys, value)
    save_yaml(file_path, data)
    sys.exit(0)


def main():
    if len(sys.argv) < 2:
        print("Usage: yaml_helper.py <command> [args...]", file=sys.stderr)
        print("Commands:", file=sys.stderr)
        print("  get <file> <path>        - Get value at path", file=sys.stderr)
        print("  exists <file> <path>     - Check if path exists (exit code)", file=sys.stderr)
        print("  set <file> <path> <value> - Set value at path", file=sys.stderr)
        sys.exit(1)
    
    command = sys.argv[1]
    args = sys.argv[2:]
    
    if command == 'get':
        cmd_get(args)
    elif command == 'exists':
        cmd_exists(args)
    elif command == 'set':
        cmd_set(args)
    else:
        print(f"Unknown command: {command}", file=sys.stderr)
        sys.exit(1)


if __name__ == '__main__':
    main()

