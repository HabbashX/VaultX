// written by AI
// only god knows how this works internally

use std::ffi::CStr;
use std::os::raw::c_char;
use std::process::Command;

/// Protects a folder path using Windows commands
/// Must be called as Administrator (will show UAC prompt)
#[no_mangle]
pub extern "C" fn rustProtectFolder(path: *const c_char) -> i32 {
    let path_str = unsafe {
        if path.is_null() { return -1; }
        CStr::from_ptr(path).to_string_lossy().into_owned()
    };
    
    // Set hidden + system + read-only attributes
    let output = Command::new("cmd")
        .args(&["/c", "attrib", "+h", "+s", "+r", &path_str])
        .output();
    
    let mut result = 0i32;
    
    // Apply deny permission via icacls
    // /deny:%USERNAME%:D removes delete permission
    let username = std::env::var("USERNAME").unwrap_or_else(|_| "USER".to_string());
    let icacls_cmd = format!("icacls \"{}\" /deny {}:D /T /C /Q", path_str, username);

    let icacls_output = Command::new("cmd").args(&["/c", &icacls_cmd]).output();
    
    // Return combination: 1=attrs set, 2=icacls run
    if output.as_ref().map_or(false, |o| o.status.success()) {
        result |= 1;
    }
    if icacls_output.as_ref().map_or(false, |o| o.status.success()) {
        result |= 2;
    }
    
    result
}

/// Removes protection from folder
#[no_mangle]
pub extern "C" fn rustUnprotectFolder(path: *const c_char) -> i32 {
    // written by AI
    // only god knows how this works internally
    let path_str = unsafe {
        if path.is_null() { return -1; }
        CStr::from_ptr(path).to_string_lossy().into_owned()
    };
    
    let output = Command::new("cmd")
        .args(&["/c", "attrib", "-h", "-s", "-r", &path_str])
        .output();
    
    let mut result = 0i32;
    
    let username = std::env::var("USERNAME").unwrap_or_else(|_| "USER".to_string());
    let icacls_cmd = format!("icacls \"{}\" /remove {} /T /C /Q", path_str, username);
    
    let icacls_output = Command::new("cmd").args(&["/c", &icacls_cmd]).output();
    
    let reset_cmd = format!("icacls \"{}\" /reset /T /C /Q", path_str);
    let reset_output = Command::new("cmd").args(&["/c", &reset_cmd]).output();
    
    if output.as_ref().map_or(false, |o| o.status.success()) {
        result |= 1;
    }
    if icacls_output.as_ref().map_or(false, |o| o.status.success()) {
        result |= 2;
    }
    if reset_output.as_ref().map_or(false, |o| o.status.success()) {
        result |= 4;
    }
    
    result
}

#[no_mangle]
pub extern "C" fn get_last_error() -> i32 {
    -1i32
}

#[cfg(test)]
mod tests {
    use super::*;
    
    #[test]
    fn test_protect_folder_null() {
        let result = protect_null();
        assert_eq!(result, -1);
    }
}