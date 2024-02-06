function cirmob = cirMatrix(z,r)
%cirmob = cirMatrix(z,r)
%   Convert eucl circle to 2x2 matrix
cirmob=[1.0,-conj(z);-z,(real(z)^2+imag(z)^2)-r*r];
end

