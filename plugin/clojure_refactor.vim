let s:p_dir = expand('<sfile>:p:h')
let s:server_is_running = 0
let s:job_id = 0

function! s:StartIfNotRunning()
    if s:server_is_running == 0
        echo 'starting plugin...'
        let jar_file_path = s:p_dir . '/../target/clj-nvim-0.1.0-SNAPSHOT-standalone.jar'
        let s:job_id = jobstart(['java', '-jar', jar_file_path], {'rpc': v:true})
        let s:server_is_running = 1
        echo 'started'
    endif
endfunction

function! CljRefactorTidyNs()
    call s:StartIfNotRunning()
    let res = rpcrequest(s:job_id, 'tidy-ns')
    return res
endfunction
